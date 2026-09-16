package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveApplicationPreviewRequest;
import in.gov.jci.hrms.dto.LeaveApplicationPreviewResponse;
import in.gov.jci.hrms.dto.LeaveApplicationRequest;
import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.dto.LeaveRoutingActionResponse;
import in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveActionType;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationAction;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.LeaveWorkflowStage;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveApplicationActionRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implements the FR-LV.5 leave-balance-reservation workflow:
 * create() -> DRAFT (no balance impact; CCS 1972 validation and the
 * authoritative debitable-days figure come from LeaveValidationService)
 * submit() -> PENDING_APPROVAL: verifies available balance
 * (credited - used - reserved >= requested), reserves it, and resolves an
 * approver via SupervisorResolutionService (FR-DOA.5/6)
 * approve() -> APPROVED: moves the reservation into used_days
 * reject()/cancel() -> REJECTED/CANCELLED: releases the reservation
 *
 * Commuted Leave is a special case throughout submit/approve/reject/cancel:
 * the application itself keeps its own Commuted LeaveType (for correct
 * display/audit), but every balance touch actually debits 2x the
 * application's totalDays from the HPL balance instead - see
 * resolveDebitTarget().
 */
@Service
@Transactional(readOnly = true)
public class LeaveApplicationService {

    private static final String ENTITY_NAME = "Leave Application";
    private static final String COMMUTED_LEAVE_CODE = "COMMUTED";
    private static final String HPL_LEAVE_CODE = "HPL";
    private static final BigDecimal COMMUTED_LEAVE_HPL_MULTIPLIER = BigDecimal.valueOf(2);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SupervisorResolutionService supervisorResolutionService;
    private final LeaveValidationService leaveValidationService;
    private final LeaveApplicationActionRepository leaveApplicationActionRepository;

    public LeaveApplicationService(LeaveApplicationRepository leaveApplicationRepository,
                                    LeaveTypeRepository leaveTypeRepository,
                                    LeaveBalanceRepository leaveBalanceRepository,
                                    EmployeeRepository employeeRepository,
                                    SupervisorResolutionService supervisorResolutionService,
                                    LeaveValidationService leaveValidationService,
                                    LeaveApplicationActionRepository leaveApplicationActionRepository) {
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.supervisorResolutionService = supervisorResolutionService;
        this.leaveValidationService = leaveValidationService;
        this.leaveApplicationActionRepository = leaveApplicationActionRepository;
    }

    /** SEC-007 remediation (docs/security/SEC_001_002_REMEDIATION.md pattern). */
    @Transactional
    public LeaveApplicationResponse create(LeaveApplicationRequest request, Long callerEmployeeId, boolean onBehalfOfOthersPermitted) {
        if (!onBehalfOfOthersPermitted && !request.employeeId().equals(callerEmployeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Not authorized to create a leave application for employee " + request.employeeId() + " on behalf of another employee");
        }
        Employee employee = resolveEmployee(request.employeeId());
        LeaveType leaveType = resolveLeaveType(request.leaveTypeId());
        LeaveSession session = resolveSession(request.leaveSession());

        BigDecimal debitableDays = leaveValidationService.validateAndComputeDebitableDays(
                employee, leaveType, request.startDate(), request.endDate(), session);
        leaveValidationService.validateTotalDaysMatches(debitableDays, request.totalDays());

        // The server-computed figure is what's persisted, not the client's -
        // they're validated to match within a small tolerance, but the
        // server's own arithmetic is the authoritative one.
        LeaveApplication application = new LeaveApplication(employee, leaveType, request.startDate(),
                request.endDate(), debitableDays, request.reason(), session);

        return LeaveApplicationResponse.from(leaveApplicationRepository.saveAndFlush(application));
    }

    /**
     * Edits a DRAFT in place - only a DRAFT can be edited (mirrors every
     * other lifecycle method's status guard). Re-runs the same CCS
     * validation create() applies, so an edit can't sneak past a rule a
     * fresh application would have been rejected for. No explicit save() -
     * the entity is already managed within this transaction, same as
     * submit()/approve()/reject()/cancel() below.
     */
    @Transactional
    public LeaveApplicationResponse update(Long id, LeaveApplicationRequest request) {
        LeaveApplication application = findOrThrow(id);
        requireStatus(application, LeaveApplicationStatus.DRAFT);

        LeaveType leaveType = resolveLeaveType(request.leaveTypeId());
        LeaveSession session = resolveSession(request.leaveSession());

        BigDecimal debitableDays = leaveValidationService.validateAndComputeDebitableDays(
                application.getEmployee(), leaveType, request.startDate(), request.endDate(), session);
        leaveValidationService.validateTotalDaysMatches(debitableDays, request.totalDays());

        application.setLeaveType(leaveType);
        application.setStartDate(request.startDate());
        application.setEndDate(request.endDate());
        application.setTotalDays(debitableDays);
        application.setReason(request.reason());
        application.setLeaveSession(session);

        return LeaveApplicationResponse.from(application);
    }

    /** Dry-run: runs the same CCS validation as create() and reports calendar-days vs. debitable-days, without persisting anything. */
    public LeaveApplicationPreviewResponse preview(LeaveApplicationPreviewRequest request) {
        Employee employee = resolveEmployee(request.employeeId());
        LeaveType leaveType = resolveLeaveType(request.leaveTypeId());
        LeaveSession session = resolveSession(request.leaveSession());
        BigDecimal calendarDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);

        try {
            BigDecimal debitableDays = leaveValidationService.validateAndComputeDebitableDays(
                    employee, leaveType, request.startDate(), request.endDate(), session);
            return new LeaveApplicationPreviewResponse(calendarDays, debitableDays, true, null);
        } catch (BusinessRuleViolationException e) {
            return new LeaveApplicationPreviewResponse(calendarDays, null, false, e.getMessage());
        }
    }

    public LeaveApplicationResponse getById(Long id) {
        return LeaveApplicationResponse.from(findOrThrow(id));
    }

    public Page<LeaveApplicationResponse> list(Pageable pageable) {
        return leaveApplicationRepository.findAll(pageable).map(LeaveApplicationResponse::from);
    }

    @Transactional
    public LeaveApplicationResponse submit(Long id) {
        LeaveApplication application = findOrThrow(id);
        requireStatus(application, LeaveApplicationStatus.DRAFT);

        DebitTarget debitTarget = resolveDebitTarget(application);
        LeaveBalance balance = resolveBalance(application.getEmployee().getId(), debitTarget.leaveType().getId(),
                application.getStartDate().getYear());

        if (balance.getAvailableDays().compareTo(debitTarget.amount()) < 0) {
            throw new InsufficientLeaveBalanceException(
                    "Insufficient leave balance: available " + balance.getAvailableDays()
                            + ", requested " + debitTarget.amount());
        }

        balance.setReservedDays(balance.getReservedDays().add(debitTarget.amount()));

        supervisorResolutionService.resolveSupervisor(application.getEmployee().getId())
                .ifPresent(resolution -> {
                    application.setApproverEmployee(resolution.employee());
                    application.setApproverPost(resolution.post());
                    application.setCurrentAssignedTo(resolution.employee());
                });

        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        application.setWorkflowStage(LeaveWorkflowStage.SUBMITTED);
        logAction(application, application.getEmployee(), LeaveActionType.SUBMIT, null, null);
        return LeaveApplicationResponse.from(application);
    }

    @Transactional
    public LeaveApplicationResponse approve(Long id) {
        return doSanction(findOrThrow(id), null, null);
    }

    /** The multi-tier routing counterpart of approve() - identical balance-debit logic, plus workflow_stage/action-log bookkeeping. Both exist because approve() (no actor/remarks) is still used by the pre-existing single-tier controller/frontend. */
    @Transactional
    public LeaveApplicationResponse sanction(Long id, String remarks, Long actingEmployeeId) {
        return doSanction(findOrThrow(id), remarks, actingEmployeeId);
    }

    private LeaveApplicationResponse doSanction(LeaveApplication application, String remarks, Long actingEmployeeId) {
        requireStatus(application, LeaveApplicationStatus.PENDING_APPROVAL);

        DebitTarget debitTarget = resolveDebitTarget(application);
        LeaveBalance balance = resolveBalance(application.getEmployee().getId(), debitTarget.leaveType().getId(),
                application.getStartDate().getYear());
        balance.setReservedDays(balance.getReservedDays().subtract(debitTarget.amount()));
        balance.setUsedDays(balance.getUsedDays().add(debitTarget.amount()));

        application.setStatus(LeaveApplicationStatus.APPROVED);
        application.setWorkflowStage(LeaveWorkflowStage.SANCTIONED);
        Employee actor = actingEmployeeId != null ? resolveEmployee(actingEmployeeId) : application.getCurrentAssignedTo();
        if (actor != null) {
            logAction(application, actor, LeaveActionType.SANCTION, null, remarks);
        }
        return LeaveApplicationResponse.from(application);
    }

    @Transactional
    public LeaveApplicationResponse reject(Long id) {
        return doReject(findOrThrow(id), null, null);
    }

    /** The multi-tier routing counterpart of reject() - same balance-release logic, plus mandatory remarks and action-log bookkeeping. */
    @Transactional
    public LeaveApplicationResponse rejectWithRemarks(Long id, String remarks, Long actingEmployeeId) {
        if (remarks == null || remarks.isBlank()) {
            throw new BusinessRuleViolationException("Remarks are mandatory when rejecting a leave application");
        }
        return doReject(findOrThrow(id), remarks, actingEmployeeId);
    }

    private LeaveApplicationResponse doReject(LeaveApplication application, String remarks, Long actingEmployeeId) {
        requireStatus(application, LeaveApplicationStatus.PENDING_APPROVAL);

        releaseReservation(application);
        application.setStatus(LeaveApplicationStatus.REJECTED);
        application.setWorkflowStage(LeaveWorkflowStage.REJECTED);
        Employee actor = actingEmployeeId != null ? resolveEmployee(actingEmployeeId) : application.getCurrentAssignedTo();
        if (actor != null) {
            logAction(application, actor, LeaveActionType.REJECT, null, remarks);
        }
        return LeaveApplicationResponse.from(application);
    }

    /**
     * Reassigns the file to a new current desk without deciding it - the application stays
     * PENDING_APPROVAL throughout (status only ever moves at sanction()/reject()). Caller
     * authorization (current assignee or admin) is enforced at the controller via
     * @leaveSec.isCurrentAssignee, not here.
     */
    @Transactional
    public LeaveApplicationResponse forward(Long id, Long forwardedToEmployeeId, String remarks, Long actingEmployeeId) {
        LeaveApplication application = findOrThrow(id);
        requireStatus(application, LeaveApplicationStatus.PENDING_APPROVAL);

        Employee forwardedTo = resolveEmployee(forwardedToEmployeeId);
        Employee actor = actingEmployeeId != null ? resolveEmployee(actingEmployeeId) : application.getCurrentAssignedTo();

        application.setCurrentAssignedTo(forwardedTo);
        application.setWorkflowStage(LeaveWorkflowStage.RECOMMENDED);
        if (actor != null) {
            logAction(application, actor, LeaveActionType.RECOMMEND_FORWARD, forwardedTo, remarks);
        }
        return LeaveApplicationResponse.from(application);
    }

    public List<LeaveRoutingActionResponse> getRoutingHistory(Long applicationId) {
        findOrThrow(applicationId);
        return leaveApplicationActionRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(LeaveRoutingActionResponse::from)
                .toList();
    }

    /**
     * Month/year-wise sanction history register - every finalized (APPROVED/REJECTED) application
     * in the period, month null/0 returns the whole year, status "ALL" (or null) applies no filter.
     * forwardedByName is the last RECOMMEND_FORWARD actor in the application's own action log, null
     * if it was decided without ever being forwarded.
     */
    public List<LeaveSanctionHistoryResponse> getSanctionsHistory(int year, Integer month, String status) {
        LocalDate periodStart = LocalDate.of(year, month != null && month > 0 ? month : 1, 1);
        LocalDate periodEnd = month != null && month > 0 ? periodStart.withDayOfMonth(periodStart.lengthOfMonth()) : LocalDate.of(year, 12, 31);

        return leaveApplicationRepository.findAll().stream()
                .filter(a -> a.getStatus() == LeaveApplicationStatus.APPROVED || a.getStatus() == LeaveApplicationStatus.REJECTED)
                .filter(a -> !a.getUpdatedAt().atZone(IST).toLocalDate().isBefore(periodStart)
                        && !a.getUpdatedAt().atZone(IST).toLocalDate().isAfter(periodEnd))
                .filter(a -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || a.getStatus().name().equalsIgnoreCase(status))
                .map(a -> {
                    List<LeaveApplicationAction> actions = leaveApplicationActionRepository.findByApplicationIdOrderByCreatedAtAsc(a.getId());
                    String forwardedByName = actions.stream()
                            .filter(action -> action.getActionType() == LeaveActionType.RECOMMEND_FORWARD)
                            .reduce((first, second) -> second)
                            .map(action -> action.getActionBy().getFullName())
                            .orElse(null);
                    String sanctionedByName = actions.stream()
                            .filter(action -> action.getActionType() == LeaveActionType.SANCTION || action.getActionType() == LeaveActionType.REJECT)
                            .findFirst()
                            .map(action -> action.getActionBy().getFullName())
                            .orElse(null);
                    return LeaveSanctionHistoryResponse.from(a, forwardedByName, sanctionedByName, a.getUpdatedAt());
                })
                .toList();
    }

    private void logAction(LeaveApplication application, Employee actionBy, LeaveActionType actionType, Employee forwardedTo, String remarks) {
        leaveApplicationActionRepository.save(new LeaveApplicationAction(application, actionBy, actionType, forwardedTo, remarks));
    }

    @Transactional
    public LeaveApplicationResponse cancel(Long id) {
        LeaveApplication application = findOrThrow(id);
        if (application.getStatus() != LeaveApplicationStatus.DRAFT
                && application.getStatus() != LeaveApplicationStatus.PENDING_APPROVAL) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " cannot be cancelled from status " + application.getStatus());
        }

        if (application.getStatus() == LeaveApplicationStatus.PENDING_APPROVAL) {
            releaseReservation(application);
        }
        application.setStatus(LeaveApplicationStatus.CANCELLED);
        return LeaveApplicationResponse.from(application);
    }

    private void releaseReservation(LeaveApplication application) {
        DebitTarget debitTarget = resolveDebitTarget(application);
        LeaveBalance balance = resolveBalance(application.getEmployee().getId(), debitTarget.leaveType().getId(),
                application.getStartDate().getYear());
        balance.setReservedDays(balance.getReservedDays().subtract(debitTarget.amount()));
    }

    private record DebitTarget(LeaveType leaveType, BigDecimal amount) {
    }

    /**
     * Commuted Leave debits 2x its own totalDays from HPL's balance instead
     * of its own (CCS 1972 Rules) - every other leave type debits its own
     * balance for exactly totalDays. The application row itself always keeps
     * displaying its real leaveType (Commuted, not HPL).
     */
    private DebitTarget resolveDebitTarget(LeaveApplication application) {
        if (COMMUTED_LEAVE_CODE.equals(application.getLeaveType().getCode())) {
            LeaveType hpl = leaveTypeRepository.findByCode(HPL_LEAVE_CODE)
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            HPL_LEAVE_CODE + " leave type is not configured - Commuted Leave cannot be processed"));
            return new DebitTarget(hpl, application.getTotalDays().multiply(COMMUTED_LEAVE_HPL_MULTIPLIER));
        }
        return new DebitTarget(application.getLeaveType(), application.getTotalDays());
    }

    private LeaveSession resolveSession(LeaveSession requested) {
        return requested != null ? requested : LeaveSession.FULL_DAY;
    }

    private void requireStatus(LeaveApplication application, LeaveApplicationStatus expected) {
        if (application.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + application.getId() + " must be " + expected
                            + " but is " + application.getStatus());
        }
    }

    private LeaveBalance resolveBalance(Long employeeId, Long leaveTypeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No leave balance provisioned for employee " + employeeId
                                + ", leave type " + leaveTypeId + ", year " + year));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private LeaveType resolveLeaveType(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Leave Type", id));
    }

    private LeaveApplication findOrThrow(Long id) {
        return leaveApplicationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
