package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DisciplinaryCaseRequest;
import in.gov.jci.hrms.dto.DisciplinaryCaseResponse;
import in.gov.jci.hrms.dto.DisciplinaryStageUpdateRequest;
import in.gov.jci.hrms.entity.DisciplinaryCase;
import in.gov.jci.hrms.entity.DisciplinaryCaseStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.PenaltyType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DisciplinaryCaseRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Disciplinary case lifecycle (SRS Phase 11). Stage transitions are a fixed
 * linear-ish state machine:
 *
 * <pre>
 * INITIATED --------------------------&gt; CLOSED
 *    |                                    ^
 *    v                                    |
 * CHARGE_SHEET_ISSUED ------------------&gt; CLOSED
 *    |                                    ^
 *    v                                    |
 * INQUIRY_IN_PROGRESS -------------------&gt; CLOSED
 *    |                                    ^
 *    v                                    |
 * REPORT_SUBMITTED --&gt; PENALTY_IMPOSED --+
 *                  \--&gt; EXONERATED ------+
 * </pre>
 *
 * Early closure (any pre-REPORT_SUBMITTED stage straight to CLOSED) is
 * allowed - real disciplinary cases get dropped. Once REPORT_SUBMITTED, the
 * only forward moves are PENALTY_IMPOSED or EXONERATED, and CLOSED is
 * terminal. Moving to PENALTY_IMPOSED requires a real (non-NONE) penaltyType
 * and penaltyEffectiveFrom, and automatically backfills a PUNISHMENT entry
 * into employee_service_book (Phase 9's e-Service Book) - the one place this
 * service reaches outside its own aggregate.
 */
@Service
@Transactional(readOnly = true)
public class DisciplinaryService {

    private static final String ENTITY_NAME = "Disciplinary Case";

    private static final Map<DisciplinaryCaseStatus, Set<DisciplinaryCaseStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DisciplinaryCaseStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.INITIATED,
                EnumSet.of(DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED, DisciplinaryCaseStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED,
                EnumSet.of(DisciplinaryCaseStatus.INQUIRY_IN_PROGRESS, DisciplinaryCaseStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.INQUIRY_IN_PROGRESS,
                EnumSet.of(DisciplinaryCaseStatus.REPORT_SUBMITTED, DisciplinaryCaseStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.REPORT_SUBMITTED,
                EnumSet.of(DisciplinaryCaseStatus.PENALTY_IMPOSED, DisciplinaryCaseStatus.EXONERATED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.PENALTY_IMPOSED, EnumSet.of(DisciplinaryCaseStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.EXONERATED, EnumSet.of(DisciplinaryCaseStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(DisciplinaryCaseStatus.CLOSED, EnumSet.noneOf(DisciplinaryCaseStatus.class));
    }

    private final DisciplinaryCaseRepository disciplinaryCaseRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeServiceBookRepository employeeServiceBookRepository;

    public DisciplinaryService(DisciplinaryCaseRepository disciplinaryCaseRepository, EmployeeRepository employeeRepository,
                                EmployeeServiceBookRepository employeeServiceBookRepository) {
        this.disciplinaryCaseRepository = disciplinaryCaseRepository;
        this.employeeRepository = employeeRepository;
        this.employeeServiceBookRepository = employeeServiceBookRepository;
    }

    @Transactional
    public DisciplinaryCaseResponse createCase(DisciplinaryCaseRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        DisciplinaryCase disciplinaryCase = new DisciplinaryCase(request.caseNumber(), employee, request.caseType());
        return DisciplinaryCaseResponse.from(save(disciplinaryCase));
    }

    @Transactional
    public DisciplinaryCaseResponse updateStage(Long caseId, DisciplinaryStageUpdateRequest request) {
        DisciplinaryCase disciplinaryCase = findOrThrow(caseId);
        DisciplinaryCaseStatus current = disciplinaryCase.getStatus();
        DisciplinaryCaseStatus target = request.targetStatus();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(DisciplinaryCaseStatus.class)).contains(target)) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + caseId + " cannot move from " + current + " to " + target);
        }

        switch (target) {
            case CHARGE_SHEET_ISSUED -> applyChargeSheetIssued(disciplinaryCase, request);
            case INQUIRY_IN_PROGRESS -> applyInquiryInProgress(disciplinaryCase, request);
            case REPORT_SUBMITTED -> { /* no additional required fields */ }
            case PENALTY_IMPOSED -> applyPenaltyImposed(disciplinaryCase, request);
            case EXONERATED -> { /* no additional required fields */ }
            case CLOSED -> { /* no additional required fields */ }
            case INITIATED -> throw new BusinessRuleViolationException("Cannot transition back to INITIATED");
        }

        if (request.remarks() != null) {
            disciplinaryCase.setRemarks(request.remarks());
        }
        disciplinaryCase.setStatus(target);
        return DisciplinaryCaseResponse.from(disciplinaryCase);
    }

    private void applyChargeSheetIssued(DisciplinaryCase disciplinaryCase, DisciplinaryStageUpdateRequest request) {
        if (request.chargeSheetDate() == null) {
            throw new BusinessRuleViolationException("chargeSheetDate is required to move to CHARGE_SHEET_ISSUED");
        }
        disciplinaryCase.setChargeSheetDate(request.chargeSheetDate());
    }

    private void applyInquiryInProgress(DisciplinaryCase disciplinaryCase, DisciplinaryStageUpdateRequest request) {
        if (request.inquiryOfficerId() == null) {
            throw new BusinessRuleViolationException("inquiryOfficerId is required to move to INQUIRY_IN_PROGRESS");
        }
        Employee inquiryOfficer = employeeRepository.findById(request.inquiryOfficerId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.inquiryOfficerId()));
        disciplinaryCase.setInquiryOfficer(inquiryOfficer);
    }

    private void applyPenaltyImposed(DisciplinaryCase disciplinaryCase, DisciplinaryStageUpdateRequest request) {
        if (request.penaltyType() == null || request.penaltyType() == PenaltyType.NONE) {
            throw new BusinessRuleViolationException("A non-NONE penaltyType is required to move to PENALTY_IMPOSED");
        }
        if (request.penaltyEffectiveFrom() == null) {
            throw new BusinessRuleViolationException("penaltyEffectiveFrom is required to move to PENALTY_IMPOSED");
        }
        disciplinaryCase.setPenaltyType(request.penaltyType());
        disciplinaryCase.setPenaltyEffectiveFrom(request.penaltyEffectiveFrom());
        disciplinaryCase.setPenaltyEffectiveTo(request.penaltyEffectiveTo());

        EmployeeServiceBook event = new EmployeeServiceBook(
                disciplinaryCase.getEmployee(), request.penaltyEffectiveFrom(), "PUNISHMENT");
        event.setOrderNumber(disciplinaryCase.getCaseNumber());
        event.setOrderDate(disciplinaryCase.getChargeSheetDate() != null
                ? disciplinaryCase.getChargeSheetDate() : request.penaltyEffectiveFrom());
        event.setEventDescription("Penalty imposed: " + request.penaltyType()
                + " (Disciplinary Case " + disciplinaryCase.getCaseNumber() + ")");
        event.setRemarks(request.remarks());
        event.setMigrated(false);
        employeeServiceBookRepository.save(event);
    }

    public DisciplinaryCaseResponse getById(Long id) {
        return DisciplinaryCaseResponse.from(findOrThrow(id));
    }

    public Page<DisciplinaryCaseResponse> list(Pageable pageable) {
        return disciplinaryCaseRepository.findAll(pageable).map(DisciplinaryCaseResponse::from);
    }

    public List<DisciplinaryCaseResponse> listByEmployee(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return disciplinaryCaseRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(DisciplinaryCaseResponse::from)
                .toList();
    }

    private DisciplinaryCase findOrThrow(Long id) {
        return disciplinaryCaseRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private DisciplinaryCase save(DisciplinaryCase disciplinaryCase) {
        try {
            return disciplinaryCaseRepository.saveAndFlush(disciplinaryCase);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " number already in use: " + disciplinaryCase.getCaseNumber());
        }
    }
}
