package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Semi-annual EL accrual (PIMS ALMS Phase 2, Section 2), run on 01-Jan and
 * 01-Jul. This is the first @Scheduled job in this codebase (see
 * SchedulingConfig) - everything before it (AttendanceLeaveDeductionService
 * etc.) was invoked inline, since nothing scheduled existed yet.
 *
 * "EOL" 1/10th deduction: this schema has no dedicated Extraordinary Leave
 * (EOL) tracking - only LWP (Leave Without Pay/Dies-Non), which the V16
 * leave-type seed already documents as this codebase's stand-in terminal
 * marker for that concept. Lacking a real EOL figure to read, this deducts
 * 1/10th of the LWP ledger debits recorded against the employee in the prior
 * half-year as the closest available proxy - an approximation, not an exact
 * implementation of the CCS(Leave) Rules EOL provision, flagged here rather
 * than silently assumed correct.
 */
@Service
@Transactional(readOnly = true)
public class ElAccrualService {

    private static final String EL_CODE = "EL";
    private static final String LWP_CODE = "LWP";
    private static final BigDecimal SEMI_ANNUAL_CREDIT = new BigDecimal("15.00");
    private static final BigDecimal HALF_SPLIT = new BigDecimal("0.50");

    private final EmployeeRepository employeeRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final Clock clock;

    @Autowired
    public ElAccrualService(EmployeeRepository employeeRepository,
                             EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                             LeaveTypeRepository leaveTypeRepository,
                             LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                             LeaveLedgerEntryRepository leaveLedgerEntryRepository) {
        this(employeeRepository, employmentCategoryRepository, leaveTypeRepository, entitlementBalanceRepository,
                leaveLedgerEntryRepository, Clock.systemDefaultZone());
    }

    /** Package-visible so tests can pin "today" - mirrors AttendanceAggregationService's Clock-injection pattern. */
    ElAccrualService(EmployeeRepository employeeRepository, EmployeeEmploymentCategoryRepository employmentCategoryRepository,
                      LeaveTypeRepository leaveTypeRepository, LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                      LeaveLedgerEntryRepository leaveLedgerEntryRepository, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 1 1,7 *")
    @Transactional
    public void runSemiAnnualAccrual() {
        LocalDate today = LocalDate.now(clock);
        runSemiAnnualAccrualFor(today);
    }

    /** Extracted so a test (or an admin re-run endpoint, if one is added later) can drive this for an arbitrary cycle date without waiting for the cron trigger. */
    @Transactional
    public void runSemiAnnualAccrualFor(LocalDate cycleDate) {
        LeaveType el = leaveTypeRepository.findByCode(EL_CODE)
                .orElseThrow(() -> new BusinessRuleViolationException("EL leave type is not configured - cannot run accrual"));
        LocalDate[] priorHalfYear = priorHalfYearWindow(cycleDate);

        List<Employee> regularEmployees = employeeRepository.findAll().stream()
                .filter(employee -> isRegular(employee.getId()))
                .toList();

        for (Employee employee : regularEmployees) {
            accrueForEmployee(employee, el, cycleDate, priorHalfYear[0], priorHalfYear[1]);
        }
    }

    private void accrueForEmployee(Employee employee, LeaveType el, LocalDate cycleDate, LocalDate priorHalfStart, LocalDate priorHalfEnd) {
        int year = cycleDate.getYear();
        LeaveEntitlementBalance balance = entitlementBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), el.getId(), year)
                .orElseGet(() -> carryForwardOrNewBalance(employee, el, year));

        BigDecimal credit = proRatedCredit(employee, cycleDate);
        BigDecimal eolDeduction = eolDeduction(employee, priorHalfStart, priorHalfEnd);
        BigDecimal netCredit = credit.subtract(eolDeduction).max(BigDecimal.ZERO);
        BigDecimal encashableCredit = netCredit.multiply(HALF_SPLIT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal enjoyableCredit = netCredit.subtract(encashableCredit);

        balance.setCreditedDays(balance.getCreditedDays().add(netCredit));
        balance.setCurrentBalance(balance.getCurrentBalance().add(netCredit));
        balance.setAvailableBalance(balance.getAvailableBalance().add(netCredit));
        balance.setEncashableCredited(balance.getEncashableCredited().add(encashableCredit));
        balance.setEncashableCurrent(balance.getEncashableCurrent().add(encashableCredit));
        balance.setEncashableAvailable(balance.getEncashableAvailable().add(encashableCredit));
        balance.setEnjoyableCredited(balance.getEnjoyableCredited().add(enjoyableCredit));
        balance.setEnjoyableCurrent(balance.getEnjoyableCurrent().add(enjoyableCredit));
        balance.setEnjoyableAvailable(balance.getEnjoyableAvailable().add(enjoyableCredit));
        entitlementBalanceRepository.saveAndFlush(balance);

        LeaveLedgerEntry entry = new LeaveLedgerEntry(employee, el, cycleDate, netCredit,
                "Semi-annual EL accrual: " + credit + " credited, less " + eolDeduction
                        + " EOL 1/10th deduction (50:50 encashable/enjoyable split: " + encashableCredit + "/" + enjoyableCredit + ")",
                LeaveLedgerSource.EL_SEMI_ANNUAL_ACCRUAL);
        leaveLedgerEntryRepository.saveAndFlush(entry);
    }

    private LeaveEntitlementBalance carryForwardOrNewBalance(Employee employee, LeaveType el, int year) {
        LeaveEntitlementBalance fresh = new LeaveEntitlementBalance(employee, el, year);
        entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), el.getId(), year - 1)
                .ifPresent(prior -> {
                    fresh.setOpeningBalance(prior.getCurrentBalance());
                    fresh.setCurrentBalance(prior.getCurrentBalance());
                    fresh.setAvailableBalance(prior.getAvailableBalance());
                    fresh.setEncashableOpening(prior.getEncashableCurrent());
                    fresh.setEncashableCurrent(prior.getEncashableCurrent());
                    fresh.setEncashableAvailable(prior.getEncashableAvailable());
                    fresh.setEnjoyableOpening(prior.getEnjoyableCurrent());
                    fresh.setEnjoyableCurrent(prior.getEnjoyableCurrent());
                    fresh.setEnjoyableAvailable(prior.getEnjoyableAvailable());
                });
        return fresh;
    }

    /**
     * The advance credit is strictly invariant at 15.00 days for a full-half employee -
     * never adjusted for a leap year's extra day. A mid-year joiner (joined after the
     * start of this half-year window) gets a pro-rata share instead, using 366 as the
     * annual denominator (29 for February) in a leap year, 365/28 otherwise.
     */
    private BigDecimal proRatedCredit(Employee employee, LocalDate cycleDate) {
        LocalDate[] window = currentHalfYearWindow(cycleDate);
        LocalDate halfStart = window[0];
        LocalDate joining = employee.getDateOfJoining();
        if (joining == null || !joining.isAfter(halfStart)) {
            return SEMI_ANNUAL_CREDIT;
        }
        LocalDate halfEnd = window[1];
        if (joining.isAfter(halfEnd)) {
            return BigDecimal.ZERO;
        }
        long daysEmployedInHalf = ChronoUnit.DAYS.between(joining, halfEnd) + 1;
        long totalDaysInHalf = ChronoUnit.DAYS.between(halfStart, halfEnd) + 1;
        return SEMI_ANNUAL_CREDIT.multiply(BigDecimal.valueOf(daysEmployedInHalf))
                .divide(BigDecimal.valueOf(totalDaysInHalf), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal eolDeduction(Employee employee, LocalDate priorHalfStart, LocalDate priorHalfEnd) {
        BigDecimal lwpDays = leaveLedgerEntryRepository
                .findByEmployeeIdAndSourceAndEntryDateBetween(employee.getId(), LeaveLedgerSource.AUTO_LATE_DEDUCTION,
                        priorHalfStart, priorHalfEnd)
                .stream()
                .filter(entry -> LWP_CODE.equals(entry.getLeaveType().getCode()))
                .map(entry -> entry.getDeltaDays().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return lwpDays.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate[] currentHalfYearWindow(LocalDate cycleDate) {
        boolean isJanuaryCycle = cycleDate.getMonthValue() == 1;
        int year = cycleDate.getYear();
        return isJanuaryCycle
                ? new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 6, 30)}
                : new LocalDate[]{LocalDate.of(year, 7, 1), LocalDate.of(year, 12, 31)};
    }

    private LocalDate[] priorHalfYearWindow(LocalDate cycleDate) {
        boolean isJanuaryCycle = cycleDate.getMonthValue() == 1;
        int priorYear = isJanuaryCycle ? cycleDate.getYear() - 1 : cycleDate.getYear();
        return isJanuaryCycle
                ? new LocalDate[]{LocalDate.of(priorYear, 7, 1), LocalDate.of(priorYear, 12, 31)}
                : new LocalDate[]{LocalDate.of(priorYear, 1, 1), LocalDate.of(priorYear, 6, 30)};
    }

    /** True when Feb of the given year has 29 days - used only for documentation/tests of the pro-rata denominator rule, not directly in the arithmetic above (ChronoUnit.DAYS.between already accounts for it). */
    static boolean isLeapFebruary(int year) {
        return Year.isLeap(year);
    }

    private boolean isRegular(Long employeeId) {
        return employmentCategoryRepository.findByEmployeeId(employeeId)
                .map(EmployeeEmploymentCategory::getEmploymentCategory)
                .map(category -> category == EmploymentCategory.REGULAR)
                .orElse(false);
    }

    /**
     * Favorable Preservation: physical EL consumption debits Enjoyable EL
     * first until it reaches 0.00, then spills over into Encashable EL.
     * Returns the (enjoyable, encashable) split actually debited, for the
     * caller to persist on LeaveApplication.debitedEnjoyableDays/
     * debitedEncashableDays. Does not itself write a ledger entry or mutate
     * a LeaveApplication - callers own that, the same separation
     * LeaveApplicationService already uses for LeaveBalance mutations.
     */
    @Transactional
    public BigDecimal[] debitFavorablePreservation(LeaveEntitlementBalance balance, BigDecimal totalDays) {
        BigDecimal fromEnjoyable = totalDays.min(balance.getEnjoyableCurrent());
        BigDecimal remaining = totalDays.subtract(fromEnjoyable);
        BigDecimal fromEncashable = remaining.min(balance.getEncashableCurrent());
        if (remaining.compareTo(fromEncashable) > 0) {
            throw new BusinessRuleViolationException(
                    "Insufficient EL balance: requested " + totalDays + ", available "
                            + balance.getEnjoyableCurrent().add(balance.getEncashableCurrent()));
        }

        balance.setEnjoyableCurrent(balance.getEnjoyableCurrent().subtract(fromEnjoyable));
        balance.setEnjoyableAvailable(balance.getEnjoyableAvailable().subtract(fromEnjoyable));
        balance.setEncashableCurrent(balance.getEncashableCurrent().subtract(fromEncashable));
        balance.setEncashableAvailable(balance.getEncashableAvailable().subtract(fromEncashable));
        balance.setAvailedDays(balance.getAvailedDays().add(totalDays));
        balance.setCurrentBalance(balance.getCurrentBalance().subtract(totalDays));
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(totalDays));

        return new BigDecimal[]{fromEnjoyable, fromEncashable};
    }
}
