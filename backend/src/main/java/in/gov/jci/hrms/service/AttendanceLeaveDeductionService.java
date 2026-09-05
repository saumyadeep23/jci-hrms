package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Implements the CL -> EL -> LWP cascade for an unauthorized-late/short-hours
 * day: debit 0.5 CL; if CL has no available balance, debit 0.5 EL instead; if
 * EL has none either, mark LEAVE_WITHOUT_PAY (LWP)/DIES-NON. Every real debit
 * writes a LeaveLedgerEntry in the same transaction as the matching
 * LeaveBalance.usedDays mutation, so the two never drift apart - except the
 * LWP case, which has no LeaveBalance at all (see the LWP LeaveType's seed
 * comment in V16) and is deliberately ledger-only.
 *
 * Called inline from AttendanceAggregationService, not from a scheduler -
 * this codebase has none. relatedDailyAttendance is the idempotency key
 * (LeaveLedgerEntryRepository.existsByRelatedDailyAttendanceId): re-running
 * evaluation for a day that already triggered a debit is a no-op here, not a
 * second debit. There is deliberately no reversal path in this delivery - if
 * a day is later corrected to no longer warrant the debit (e.g. HR fixes a
 * punch), the earlier debit stands and must be corrected manually; flagged
 * as a known gap, not silently handled.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceLeaveDeductionService {

    private static final BigDecimal DEBIT_AMOUNT = new BigDecimal("0.5");
    private static final DateTimeFormatter DESCRIPTION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;

    public AttendanceLeaveDeductionService(LeaveBalanceRepository leaveBalanceRepository,
                                            LeaveTypeRepository leaveTypeRepository,
                                            LeaveLedgerEntryRepository leaveLedgerEntryRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
    }

    @Transactional
    public void debitForUnauthorizedAttendance(Employee employee, DailyAttendance dailyAttendance) {
        if (leaveLedgerEntryRepository.existsByRelatedDailyAttendanceId(dailyAttendance.getId())) {
            return;
        }

        LocalDate date = dailyAttendance.getAttendanceDate();
        String dateText = date.format(DESCRIPTION_DATE_FORMAT);
        int year = date.getYear();

        LeaveType cl = resolveLeaveTypeByCode("CL");
        Optional<LeaveBalance> clBalance = availableBalance(employee.getId(), cl.getId(), year);
        if (clBalance.isPresent()) {
            debit(employee, cl, clBalance.get(), date, dailyAttendance,
                    "Auto-debited 0.5 CL for unauthorized late attendance / short hours on " + dateText);
            return;
        }

        LeaveType el = resolveLeaveTypeByCode("EL");
        Optional<LeaveBalance> elBalance = availableBalance(employee.getId(), el.getId(), year);
        if (elBalance.isPresent()) {
            debit(employee, el, elBalance.get(), date, dailyAttendance,
                    "Auto-debited 0.5 EL for unauthorized late attendance / short hours on " + dateText
                            + " (CL balance exhausted)");
            return;
        }

        LeaveType lwp = resolveLeaveTypeByCode("LWP");
        LeaveLedgerEntry entry = new LeaveLedgerEntry(employee, lwp, date, DEBIT_AMOUNT.negate(),
                "Marked LEAVE_WITHOUT_PAY (LWP)/DIES-NON for unauthorized late attendance / short hours on " + dateText
                        + " - CL and EL balances exhausted",
                LeaveLedgerSource.AUTO_LATE_DEDUCTION);
        entry.setRelatedDailyAttendance(dailyAttendance);
        leaveLedgerEntryRepository.saveAndFlush(entry);
        dailyAttendance.setAutoPenaltyDebited(true);
    }

    /**
     * Absent (not provisioned for this employee/year) is treated the same as
     * insufficient, not as a hard error - unlike LeaveApplicationService's
     * human-driven submit(), this runs unattended, and a per-employee
     * provisioning gap shouldn't abort evaluation of an entire month.
     */
    private Optional<LeaveBalance> availableBalance(Long employeeId, Long leaveTypeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .filter(balance -> balance.getAvailableDays().compareTo(DEBIT_AMOUNT) >= 0);
    }

    private void debit(Employee employee, LeaveType leaveType, LeaveBalance balance, LocalDate date,
                        DailyAttendance dailyAttendance, String description) {
        balance.setUsedDays(balance.getUsedDays().add(DEBIT_AMOUNT));
        LeaveLedgerEntry entry = new LeaveLedgerEntry(employee, leaveType, date, DEBIT_AMOUNT.negate(), description,
                LeaveLedgerSource.AUTO_LATE_DEDUCTION);
        entry.setRelatedDailyAttendance(dailyAttendance);
        leaveLedgerEntryRepository.saveAndFlush(entry);
        dailyAttendance.setAutoPenaltyDebited(true);
    }

    private LeaveType resolveLeaveTypeByCode(String code) {
        return leaveTypeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        code + " leave type is not configured - cannot process automatic attendance-based leave deduction"));
    }
}
