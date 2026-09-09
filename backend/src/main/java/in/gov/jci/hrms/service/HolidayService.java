package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.HolidayCalendarResponse;
import in.gov.jci.hrms.dto.HolidayRequest;
import in.gov.jci.hrms.dto.HolidayResponse;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class HolidayService {

    private static final String ENTITY_NAME = "Holiday";
    private static final String CENTRAL = "CENTRAL";
    /** JCI's Head Office is in Kolkata - HO-posted employees (no RO/DPC) resolve to this fixed state. */
    private static final String HO_STATE = "West Bengal";
    private static final String HO_LABEL = "HO - Kolkata, West Bengal";
    private static final int UPCOMING_HOLIDAY_WINDOW_DAYS = 90;

    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;

    public HolidayService(HolidayRepository holidayRepository, EmployeeRepository employeeRepository) {
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public HolidayResponse create(HolidayRequest request) {
        Holiday holiday = new Holiday(request.holidayDate(), request.name(), request.holidayType(), request.state());
        return HolidayResponse.from(save(holiday));
    }

    public HolidayResponse getById(Long id) {
        return HolidayResponse.from(findOrThrow(id));
    }

    public Page<HolidayResponse> list(Pageable pageable) {
        return holidayRepository.findAll(pageable).map(HolidayResponse::from);
    }

    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request) {
        Holiday holiday = findOrThrow(id);
        holiday.setHolidayDate(request.holidayDate());
        holiday.setName(request.name());
        holiday.setHolidayType(request.holidayType());
        holiday.setState(request.state());
        return HolidayResponse.from(save(holiday));
    }

    /**
     * Union of national/CENTRAL gazetted holidays, the caller's state-specific
     * gazetted holidays, and restricted holidays available at their location,
     * for one calendar month - plus the nearest upcoming applicable holiday
     * within a 90-day lookahead window (which may fall outside that month).
     */
    public HolidayCalendarResponse getMyCalendar(Long employeeId, int year, int month) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        String employeeState = resolveState(employee);
        String officeLabel = resolveOfficeLabel(employee, employeeState);

        YearMonth yearMonth = YearMonth.of(year, month);
        List<Holiday> monthHolidays = holidayRepository.findByHolidayDateBetween(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        List<HolidayResponse> applicableThisMonth = filterApplicable(monthHolidays, employeeState).stream()
                .map(HolidayResponse::from)
                .toList();

        LocalDate today = LocalDate.now();
        List<Holiday> lookaheadHolidays = holidayRepository.findByHolidayDateBetween(
                today, today.plusDays(UPCOMING_HOLIDAY_WINDOW_DAYS));
        Optional<Holiday> upcoming = filterApplicable(lookaheadHolidays, employeeState).stream()
                .filter(h -> !h.getHolidayDate().isBefore(today))
                .min(Comparator.comparing(Holiday::getHolidayDate));

        return new HolidayCalendarResponse(
                officeLabel,
                employeeState,
                applicableThisMonth,
                upcoming.map(Holiday::getHolidayDate).orElse(null),
                upcoming.map(Holiday::getName).orElse(null)
        );
    }

    /**
     * Restricted holidays the caller can pick from for a whole year (e.g.
     * the Combined CL+RH form's RH dropdown) - national/CENTRAL rows plus
     * the caller's own state's rows only. Without this scoping, a festival
     * published as a separate row per state (V39: multi-state) - e.g. a
     * Ganesh Chaturthi row each for Maharashtra, Karnataka, Gujarat, ... -
     * would surface every state's row to every employee, showing as
     * several duplicate-looking entries for what is really one holiday per
     * location. Deduplicated by (date, name) as a last-resort safety net in
     * case the master data itself ever has a true accidental duplicate.
     */
    public List<HolidayResponse> getMyRestrictedHolidays(Long employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        String employeeState = resolveState(employee);

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<Holiday> yearHolidays = holidayRepository.findByHolidayDateBetween(yearStart, yearEnd);

        LinkedHashMap<String, Holiday> deduped = new LinkedHashMap<>();
        for (Holiday holiday : yearHolidays) {
            if (holiday.getHolidayType() != HolidayType.RESTRICTED || !isApplicable(holiday, employeeState)) {
                continue;
            }
            deduped.putIfAbsent(holiday.getHolidayDate() + "|" + holiday.getName(), holiday);
        }

        return deduped.values().stream()
                .sorted(Comparator.comparing(Holiday::getHolidayDate))
                .map(HolidayResponse::from)
                .toList();
    }

    private List<Holiday> filterApplicable(List<Holiday> holidays, String employeeState) {
        return holidays.stream().filter(h -> isApplicable(h, employeeState)).toList();
    }

    private boolean isApplicable(Holiday holiday, String employeeState) {
        boolean isNational = holiday.getState() == null || CENTRAL.equalsIgnoreCase(holiday.getState());
        if (isNational) {
            return true;
        }
        return holiday.getState().equalsIgnoreCase(employeeState);
    }

    private String resolveState(Employee employee) {
        DepartmentalPurchaseCentre dpc = employee.getDepartmentalPurchaseCentre();
        if (dpc != null) {
            return dpc.getState();
        }
        RegionalOffice ro = employee.getRegionalOffice();
        if (ro != null) {
            return ro.getState();
        }
        return HO_STATE;
    }

    private String resolveOfficeLabel(Employee employee, String employeeState) {
        DepartmentalPurchaseCentre dpc = employee.getDepartmentalPurchaseCentre();
        if (dpc != null) {
            return "DPC - " + dpc.getName() + ", " + employeeState;
        }
        RegionalOffice ro = employee.getRegionalOffice();
        if (ro != null) {
            return "RO - " + ro.getName() + ", " + employeeState;
        }
        return HO_LABEL;
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = findOrThrow(id);
        holiday.setDeletedAt(Instant.now());
    }

    private Holiday findOrThrow(Long id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private Holiday save(Holiday holiday) {
        try {
            return holidayRepository.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " already exists for date: " + holiday.getHolidayDate());
        }
    }
}
