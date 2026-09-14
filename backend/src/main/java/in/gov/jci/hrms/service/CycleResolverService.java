package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.repository.HrmsPayrollCycleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Resolves the JCIECCS payroll cycle (26th of the previous month to 25th of the current month,
 * cycle_code "YYYY-MM") for a given date or target month, get-or-create against hrms_payroll_cycle
 * (V87). Both {@link #resolveForDate} and {@link #resolveForYearMonth} funnel through
 * {@link #resolveForYearMonth(int, int)} so the two entry points can never disagree on a cycle's bounds.
 */
@Service
@Transactional(readOnly = true)
public class CycleResolverService {

    private final HrmsPayrollCycleRepository cycleRepository;

    public CycleResolverService(HrmsPayrollCycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    /**
     * Disbursement/collection date -&gt; cycle, per the spec's boundary rule: on or before the 25th falls
     * in the current calendar month's cycle; on or after the 26th falls in next month's cycle.
     */
    public HrmsPayrollCycle resolveForDate(LocalDate date) {
        YearMonth targetMonth = date.getDayOfMonth() <= 25 ? YearMonth.from(date) : YearMonth.from(date).plusMonths(1);
        return resolveForYearMonth(targetMonth.getYear(), targetMonth.getMonthValue());
    }

    /** Direct construction for a known target (year, month) - e.g. a PayrollBatch's own salYear/salMonth. */
    public HrmsPayrollCycle resolveForYearMonth(int year, int month) {
        YearMonth targetMonth = YearMonth.of(year, month);
        String cycleCode = "%04d-%02d".formatted(year, month);

        return cycleRepository.findByCycleCode(cycleCode).orElseGet(() -> createCycle(cycleCode, targetMonth));
    }

    /**
     * Term Loan repayment starts in the disbursement cycle itself; Emergency Loan repayment starts the
     * cycle after disbursement (spec section 2).
     */
    public HrmsPayrollCycle repaymentStartCycle(boolean sameCycleAsDisbursement, HrmsPayrollCycle disbursementCycle) {
        if (sameCycleAsDisbursement) {
            return disbursementCycle;
        }
        YearMonth disbursementMonth = YearMonth.parse(disbursementCycle.getCycleCode()).plusMonths(1);
        return resolveForYearMonth(disbursementMonth.getYear(), disbursementMonth.getMonthValue());
    }

    /**
     * A concurrent request resolving the same brand-new cycle could otherwise race past the
     * findByCycleCode check above and both attempt the insert - the unique constraint on cycle_code is
     * the backstop; on that race the loser retries the read instead of failing.
     */
    @Transactional
    HrmsPayrollCycle createCycle(String cycleCode, YearMonth targetMonth) {
        LocalDate periodEnd = targetMonth.atDay(25);
        LocalDate periodStart = targetMonth.minusMonths(1).atDay(26);
        try {
            return cycleRepository.saveAndFlush(new HrmsPayrollCycle(cycleCode, periodStart, periodEnd));
        } catch (DataIntegrityViolationException raceLoserRetry) {
            return cycleRepository.findByCycleCode(cycleCode).orElseThrow(() -> raceLoserRetry);
        }
    }
}
