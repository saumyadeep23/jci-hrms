package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.HolidayBulkUploadResult;
import in.gov.jci.hrms.dto.HolidayMasterCreateRequest;
import in.gov.jci.hrms.dto.HolidayMasterRow;
import in.gov.jci.hrms.dto.HolidayMasterUpdateRequest;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.AttendancePayrollCutoffRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * State-wise Yearly Holiday &amp; RH Management Publisher - a fan-out layer
 * on top of the pre-existing Holiday entity/HolidayRepository (which stays
 * single-state per row and untouched in its ESS-facing behavior, see
 * HolidayService/HolidayController at /api/holidays). "ALL"/national is
 * represented the same way HolidayService already recognizes it -
 * Holiday.state == null - rather than literally persisting the string "ALL",
 * so /api/holidays' getMyCalendar keeps working unmodified against rows
 * created here.
 */
@Service
@Transactional(readOnly = true)
public class HolidayMasterService {

    private static final String ENTITY_NAME = "Holiday";
    private static final String ALL_MARKER = "ALL";
    private static final String NATIONAL_STATE_CODE = "ALL";
    private static final String NATIONAL_STATE_LABEL = "All India / Central";

    private final HolidayRepository holidayRepository;
    private final StateMasterRepository stateMasterRepository;
    private final AttendancePayrollCutoffRepository attendancePayrollCutoffRepository;

    public HolidayMasterService(HolidayRepository holidayRepository, StateMasterRepository stateMasterRepository,
                                 AttendancePayrollCutoffRepository attendancePayrollCutoffRepository) {
        this.holidayRepository = holidayRepository;
        this.stateMasterRepository = stateMasterRepository;
        this.attendancePayrollCutoffRepository = attendancePayrollCutoffRepository;
    }

    /**
     * stateCode null/blank/"ALL" applies no state filter at all (every
     * holiday, national and every state's own, is returned) - matching a
     * conventional admin "All" filter option. A specific state code returns
     * that state's own rows UNION every national/Central row, since a
     * national holiday applies everywhere regardless of which state is
     * selected; it previously excluded national rows entirely when a
     * specific state was selected, which was wrong (a WB filter should
     * still surface Republic Day).
     */
    public List<HolidayMasterRow> list(int year, String stateCode, String type) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(yearStart, yearEnd);

        return holidays.stream()
                .filter(h -> matchesState(h, stateCode))
                .filter(h -> matchesType(h, type))
                .sorted(Comparator.comparing(Holiday::getHolidayDate))
                .map(this::toRow)
                .toList();
    }

    private boolean matchesState(Holiday holiday, String stateCode) {
        if (stateCode == null || stateCode.isBlank() || ALL_MARKER.equalsIgnoreCase(stateCode)) {
            return true;
        }
        if (isNational(holiday)) {
            return true;
        }
        StateMaster state = stateMasterRepository.findByStateCode(stateCode).orElse(null);
        return state != null && state.getStateName().equalsIgnoreCase(holiday.getState());
    }

    private boolean matchesType(Holiday holiday, String type) {
        if (type == null || type.isBlank() || ALL_MARKER.equalsIgnoreCase(type)) {
            return true;
        }
        try {
            return holiday.getHolidayType() == HolidayType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new MasterDataValidationException("Unknown holiday type filter: " + type);
        }
    }

    @Transactional
    public List<HolidayMasterRow> create(HolidayMasterCreateRequest request) {
        return createRows(request.holidayName(), request.holidayDate(), request.holidayType(),
                request.stateCodes(), request.description());
    }

    private List<HolidayMasterRow> createRows(String name, LocalDate date, HolidayType type, List<String> stateCodes,
                                               String description) {
        List<StateMaster> activeStates = stateMasterRepository.findByActiveTrueOrderByStateNameAsc();
        Set<String> activeCodes = activeStates.stream().map(s -> s.getStateCode().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean isNational = stateCodes.stream().anyMatch(ALL_MARKER::equalsIgnoreCase)
                || stateCodes.stream().map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toSet()).containsAll(activeCodes);

        if (isNational) {
            Holiday holiday = new Holiday(date, name, type, null);
            holiday.setDescription(description);
            return List.of(toRow(save(holiday)));
        }

        List<HolidayMasterRow> created = new ArrayList<>();
        for (String code : stateCodes) {
            StateMaster state = stateMasterRepository.findByStateCode(code)
                    .orElseThrow(() -> new MasterDataValidationException("Unknown state code: " + code));
            Holiday holiday = new Holiday(date, name, type, state.getStateName());
            holiday.setDescription(description);
            created.add(toRow(save(holiday)));
        }
        return created;
    }

    @Transactional
    public HolidayMasterRow update(Long id, HolidayMasterUpdateRequest request) {
        Holiday holiday = findOrThrow(id);
        holiday.setHolidayDate(request.holidayDate());
        holiday.setName(request.holidayName());
        holiday.setHolidayType(request.holidayType());
        holiday.setDescription(request.description());
        holiday.setState(resolveStateName(request.stateCode()));
        return toRow(save(holiday));
    }

    private String resolveStateName(String stateCode) {
        if (stateCode == null || ALL_MARKER.equalsIgnoreCase(stateCode)) {
            return null;
        }
        return stateMasterRepository.findByStateCode(stateCode)
                .orElseThrow(() -> new MasterDataValidationException("Unknown state code: " + stateCode))
                .getStateName();
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = findOrThrow(id);
        attendancePayrollCutoffRepository
                .findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualAndFrozenTrue(
                        holiday.getHolidayDate(), holiday.getHolidayDate())
                .ifPresent(cutoff -> {
                    throw new BusinessRuleViolationException(
                            "Cannot delete " + holiday.getHolidayDate() + " - it falls within a locked payroll cycle ("
                                    + cutoff.getPeriodStart() + " to " + cutoff.getPeriodEnd() + ")");
                });
        holiday.setDeletedAt(Instant.now());
    }

    /**
     * Lenient row-by-row CSV import - one malformed/duplicate row doesn't
     * sink the whole annual gazette file. Columns: holidayDate (yyyy-MM-dd,
     * matching every other CSV import in this codebase - see
     * LeaveBaselineTakeOnPage's template), holidayName, holidayType
     * (GAZETTED/RESTRICTED), stateCodes (semicolon-separated within the
     * cell, e.g. "WB;BR" or "ALL"), isRestricted (accepted for template
     * completeness but not read - holidayType is the single source of
     * truth, see HolidayMasterCreateRequest's javadoc).
     */
    @Transactional
    public HolidayBulkUploadResult bulkUpload(MultipartFile file) {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(line -> !line.isBlank()).toList();
        } catch (IOException e) {
            throw new BusinessRuleViolationException("Could not read the uploaded CSV file: " + e.getMessage());
        }

        List<String> dataLines = !lines.isEmpty() && lines.get(0).toLowerCase(Locale.ROOT).startsWith("holidaydate")
                ? lines.subList(1, lines.size()) : lines;

        List<String> errors = new ArrayList<>();
        int created = 0;
        for (int i = 0; i < dataLines.size(); i++) {
            int lineNumber = i + 2;
            String[] cols = dataLines.get(i).split(",", -1);
            try {
                if (cols.length < 4) {
                    throw new BusinessRuleViolationException("Expected at least 4 columns (holidayDate,holidayName,holidayType,stateCodes)");
                }
                LocalDate date = LocalDate.parse(cols[0].trim());
                String name = cols[1].trim();
                HolidayType type = HolidayType.valueOf(cols[2].trim().toUpperCase(Locale.ROOT));
                List<String> stateCodes = Arrays.stream(cols[3].trim().split(";"))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (name.isEmpty() || stateCodes.isEmpty()) {
                    throw new BusinessRuleViolationException("holidayName and stateCodes are required");
                }
                created += createRows(name, date, type, stateCodes, null).size();
            } catch (Exception e) {
                errors.add("Line " + lineNumber + ": " + e.getMessage());
            }
        }
        return new HolidayBulkUploadResult(dataLines.size(), created, errors);
    }

    private HolidayMasterRow toRow(Holiday holiday) {
        boolean national = isNational(holiday);
        String stateCode;
        String stateName;
        if (national) {
            stateCode = NATIONAL_STATE_CODE;
            stateName = NATIONAL_STATE_LABEL;
        } else {
            stateCode = stateMasterRepository.findByStateNameIgnoreCase(holiday.getState())
                    .map(StateMaster::getStateCode)
                    .orElse(holiday.getState());
            stateName = holiday.getState();
        }
        return new HolidayMasterRow(holiday.getId(), holiday.getHolidayDate(), holiday.getName(), holiday.getHolidayType(),
                holiday.getHolidayType() == HolidayType.RESTRICTED, stateCode, stateName, holiday.getDescription());
    }

    private boolean isNational(Holiday holiday) {
        String state = holiday.getState();
        return state == null || "CENTRAL".equalsIgnoreCase(state) || ALL_MARKER.equalsIgnoreCase(state);
    }

    private Holiday findOrThrow(Long id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private Holiday save(Holiday holiday) {
        try {
            return holidayRepository.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " already exists for " + holiday.getHolidayDate()
                    + " / " + (holiday.getState() == null ? NATIONAL_STATE_LABEL : holiday.getState()));
        }
    }
}
