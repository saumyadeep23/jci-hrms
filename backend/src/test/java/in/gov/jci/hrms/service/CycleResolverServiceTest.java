package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 26th-of-previous-month-to-25th-of-current-month boundary rule (spec section 2) - dates before,
 * on, and after both boundary days must resolve to the correct cycle_code/period bounds, and repeated
 * resolution of the same target month must get-or-create the same row rather than duplicating it.
 */
@SpringBootTest
@Transactional
class CycleResolverServiceTest {

    @Autowired
    private CycleResolverService cycleResolverService;

    @Test
    void dateBeforeThe25th_resolvesToCurrentCalendarMonthCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 1, 10));

        assertThat(cycle.getCycleCode()).isEqualTo("2026-01");
        assertThat(cycle.getPeriodStart()).isEqualTo(LocalDate.of(2025, 12, 26));
        assertThat(cycle.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 25));
    }

    @Test
    void dateOnThe25th_resolvesToCurrentCalendarMonthCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 1, 25));

        assertThat(cycle.getCycleCode()).isEqualTo("2026-01");
        assertThat(cycle.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 25));
    }

    @Test
    void dateOnThe26th_resolvesToNextCalendarMonthCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 1, 26));

        assertThat(cycle.getCycleCode()).isEqualTo("2026-02");
        assertThat(cycle.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 26));
        assertThat(cycle.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 25));
    }

    @Test
    void dateAfterThe26th_resolvesToNextCalendarMonthCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 1, 31));

        assertThat(cycle.getCycleCode()).isEqualTo("2026-02");
    }

    @Test
    void resolveForYearMonth_isIdempotent_getOrCreateReturnsTheSameRow() {
        HrmsPayrollCycle first = cycleResolverService.resolveForYearMonth(2026, 3);
        HrmsPayrollCycle second = cycleResolverService.resolveForYearMonth(2026, 3);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void repaymentStartCycle_termLoan_isTheDisbursementCycleItself() {
        HrmsPayrollCycle disbursementCycle = cycleResolverService.resolveForYearMonth(2026, 4);

        HrmsPayrollCycle repaymentStart = cycleResolverService.repaymentStartCycle(true, disbursementCycle);

        assertThat(repaymentStart.getId()).isEqualTo(disbursementCycle.getId());
    }

    @Test
    void repaymentStartCycle_emergencyLoan_isTheCycleAfterDisbursement() {
        HrmsPayrollCycle disbursementCycle = cycleResolverService.resolveForYearMonth(2026, 4);

        HrmsPayrollCycle repaymentStart = cycleResolverService.repaymentStartCycle(false, disbursementCycle);

        assertThat(repaymentStart.getCycleCode()).isEqualTo("2026-05");
    }
}
