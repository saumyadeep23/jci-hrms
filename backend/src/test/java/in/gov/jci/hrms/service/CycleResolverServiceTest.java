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

    /** Phase 5 hardening (spec section 6) - the 26th-of-December must roll over into NEXT YEAR's January
     * cycle, not wrap within the same year - the one boundary case most likely to hide a YearMonth
     * arithmetic bug. */
    @Test
    void decemberThe26th_rollsOverIntoNextYearJanuaryCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 12, 26));

        assertThat(cycle.getCycleCode()).isEqualTo("2027-01");
        assertThat(cycle.getPeriodStart()).isEqualTo(LocalDate.of(2026, 12, 26));
        assertThat(cycle.getPeriodEnd()).isEqualTo(LocalDate.of(2027, 1, 25));
    }

    @Test
    void decemberThe25th_staysInDecemberCycleOfTheSameYear() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 12, 25));

        assertThat(cycle.getCycleCode()).isEqualTo("2026-12");
    }

    /** 26-Feb rolls into March regardless of whether February has 28 or 29 days that year - the boundary
     * is purely day-of-month (<=25 vs >=26), never "days remaining in the month." */
    @Test
    void februaryThe26th_resolvesToMarchCycle_leapYearOrNot() {
        HrmsPayrollCycle leapYearCycle = cycleResolverService.resolveForDate(LocalDate.of(2028, 2, 26)); // 2028 is a leap year
        HrmsPayrollCycle nonLeapYearCycle = cycleResolverService.resolveForDate(LocalDate.of(2026, 2, 26)); // 2026 is not

        assertThat(leapYearCycle.getCycleCode()).isEqualTo("2028-03");
        assertThat(nonLeapYearCycle.getCycleCode()).isEqualTo("2026-03");
    }

    /** 29-Feb only exists in a leap year and is comfortably >25, so it must resolve into March exactly like
     * any other late-February date - proves no special-case mishandling of the leap day itself. */
    @Test
    void februaryThe29th_leapYearOnly_resolvesToMarchCycle() {
        HrmsPayrollCycle cycle = cycleResolverService.resolveForDate(LocalDate.of(2028, 2, 29));

        assertThat(cycle.getCycleCode()).isEqualTo("2028-03");
    }
}
