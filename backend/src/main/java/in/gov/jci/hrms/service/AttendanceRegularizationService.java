package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AttendanceRegularizationRequest;
import in.gov.jci.hrms.dto.AttendanceRegularizationResponse;
import in.gov.jci.hrms.dto.RegularizationDecisionRequest;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.AttendanceRegularizationApplication;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.AttendanceRegularizationApplicationRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * HoD approval workflow for a REQUIRES_REGULARIZATION/UNAUTHORIZED_LATE day
 * (PIMS ALMS Phase 2, Section 5 - the one genuinely missing piece of the
 * attendance synthesizer/penalty cascade; the order-of-precedence,
 * flex-grace, and 3rd-strike-debit logic already exist in
 * AttendanceAggregationService/AttendanceLeaveDeductionService). Approving a
 * regularization recomputes work duration from the corrected times, resets
 * detail_status to PRESENT, and - only if AttendanceLeaveDeductionService
 * actually debited a 0.5-day penalty for this day (auto_penalty_debited) -
 * refunds it with a reversing ledger entry.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceRegularizationService {

    private static final BigDecimal PENALTY_AMOUNT = new BigDecimal("0.5");
    /** ABSENT/HALF_DAY_ABSENT widened in alongside the original late/short-hours pair - same submit()/applyRegularization() flow handles a missing punch exactly as it handles a wrong one (both just need corrected in/out times and HoD sign-off). */
    private static final Set<AttendanceDetailStatus> REGULARIZABLE_STATUSES = Set.of(
            AttendanceDetailStatus.REQUIRES_REGULARIZATION, AttendanceDetailStatus.UNAUTHORIZED_LATE,
            AttendanceDetailStatus.ABSENT, AttendanceDetailStatus.HALF_DAY_ABSENT);

    private final AttendanceRegularizationApplicationRepository regularizationRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final SupervisorResolutionService supervisorResolutionService;

    public AttendanceRegularizationService(AttendanceRegularizationApplicationRepository regularizationRepository,
                                            DailyAttendanceRepository dailyAttendanceRepository,
                                            LeaveBalanceRepository leaveBalanceRepository,
                                            LeaveLedgerEntryRepository leaveLedgerEntryRepository,
                                            EmployeeRepository employeeRepository,
                                            SupervisorResolutionService supervisorResolutionService) {
        this.regularizationRepository = regularizationRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.employeeRepository = employeeRepository;
        this.supervisorResolutionService = supervisorResolutionService;
    }

    @Transactional
    public AttendanceRegularizationResponse submit(AttendanceRegularizationRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
        DailyAttendance dailyAttendance = dailyAttendanceRepository
                .findByEmployeeIdAndAttendanceDate(employee.getId(), request.attendanceDate())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No attendance record exists for employee " + employee.getId() + " on " + request.attendanceDate()));
        if (!REGULARIZABLE_STATUSES.contains(dailyAttendance.getDetailStatus())) {
            throw new BusinessRuleViolationException(
                    "Attendance date " + request.attendanceDate() + " is not eligible for regularization (status: "
                            + dailyAttendance.getDetailStatus() + ")");
        }

        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(
                employee, request.attendanceDate(), dailyAttendance, request.reasonCode(), request.remarks(),
                request.correctedInTime(), request.correctedOutTime());
        supervisorResolutionService.resolveSupervisor(employee.getId())
                .ifPresent(resolution -> application.setDesignatedApprover(resolution.employee()));

        return AttendanceRegularizationResponse.from(regularizationRepository.saveAndFlush(application));
    }

    @Transactional
    public AttendanceRegularizationResponse approve(Long id, RegularizationDecisionRequest decision) {
        AttendanceRegularizationApplication application = findOrThrow(id);
        if (application.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessRuleViolationException("Regularization application " + id + " has already been decided");
        }

        if (Boolean.TRUE.equals(decision.approve())) {
            applyRegularization(application);
            application.setApprovalStatus(ApprovalStatus.APPROVED);
        } else {
            application.setApprovalStatus(ApprovalStatus.REJECTED);
        }
        application.setApprovedAt(Instant.now());
        application.setApproverRemarks(decision.remarks());
        return AttendanceRegularizationResponse.from(application);
    }

    private void applyRegularization(AttendanceRegularizationApplication application) {
        DailyAttendance dailyAttendance = application.getDailyAttendance();
        if (dailyAttendance == null) {
            throw new BusinessRuleViolationException(
                    "Regularization application " + application.getId() + " has no linked attendance record");
        }

        long workedMinutes = Duration.between(application.getCorrectedInTime(), application.getCorrectedOutTime()).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(workedMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        dailyAttendance.applyDetail(AttendanceDetailStatus.PRESENT,
                "Regularized by HoD approval - corrected punches applied", dailyAttendance.getLeaveApplication(),
                dailyAttendance.getTourRequest());
        dailyAttendance.setInTime(application.getCorrectedInTime());
        dailyAttendance.setOutTime(application.getCorrectedOutTime());
        dailyAttendance.setTotalWorkingHours(hours);
        dailyAttendance.setTotalWorkMinutes((int) workedMinutes);
        dailyAttendance.setRegularized(true);

        if (dailyAttendance.isAutoPenaltyDebited()) {
            refundPenalty(application.getEmployee(), dailyAttendance);
            dailyAttendance.setAutoPenaltyDebited(false);
        }
        dailyAttendanceRepository.saveAndFlush(dailyAttendance);
    }

    /** Reverses the original AttendanceLeaveDeductionService debit: credits the same leave type's usedDays back (LWP has no LeaveBalance row, so only the ledger entry applies there) and writes an equal-and-opposite ledger entry. */
    private void refundPenalty(Employee employee, DailyAttendance dailyAttendance) {
        LeaveLedgerEntry originalDebit = leaveLedgerEntryRepository.findByRelatedDailyAttendanceId(dailyAttendance.getId())
                .orElse(null);
        if (originalDebit == null) {
            return;
        }

        leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                        employee.getId(), originalDebit.getLeaveType().getId(), dailyAttendance.getAttendanceDate().getYear())
                .ifPresent(balance -> creditBack(balance));

        LeaveLedgerEntry refund = new LeaveLedgerEntry(employee, originalDebit.getLeaveType(), dailyAttendance.getAttendanceDate(),
                PENALTY_AMOUNT, "Refund of auto-debited " + PENALTY_AMOUNT + " " + originalDebit.getLeaveType().getCode()
                        + " on regularization approval for " + dailyAttendance.getAttendanceDate(),
                LeaveLedgerSource.ATTENDANCE_PENALTY_REFUND);
        refund.setRelatedDailyAttendance(dailyAttendance);
        leaveLedgerEntryRepository.saveAndFlush(refund);
    }

    private void creditBack(LeaveBalance balance) {
        balance.setUsedDays(balance.getUsedDays().subtract(PENALTY_AMOUNT).max(BigDecimal.ZERO));
        leaveBalanceRepository.saveAndFlush(balance);
    }

    private AttendanceRegularizationApplication findOrThrow(Long id) {
        return regularizationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Attendance Regularization Application", id));
    }
}
