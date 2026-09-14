package in.gov.jci.hrms.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure, no-DB unit coverage for CpfInterestCalculator - the single source of truth for CPF interest math,
 * used by CpfInterestComputationService (annual run, interim settlement) and CpfTrustPassbookService (live
 * projection). End-to-end wiring against real ledger data (the "opening balance date" lookups that feed
 * monthlyProduct in production) is covered separately by CpfInterestComputationServiceTest and
 * CpfInterimSettlementTest - this file tests the calculator's own arithmetic and date/index utilities in
 * isolation.
 */
class CpfInterestCalculatorTest {

    private static final BigDecimal RATE_8_5 = new BigDecimal("8.50");

    // --- Test 1: opening balance, full FY ---

    @Test
    void calculate_openingBalanceFullYear_appliesTwelveMonthProduct() {
        // 100000 held for all 12 months (opening balance, never touched) -> monthlyProduct = 100000*12 = 1200000.
        BigDecimal monthlyProduct = new BigDecimal("100000").multiply(BigDecimal.valueOf(12));

        var result = CpfInterestCalculator.calculate(monthlyProduct, BigDecimal.ZERO, BigDecimal.ZERO, RATE_8_5,
                12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        // 1200000 * 8.5 / 1200 = 8500.00
        assertThat(result.totalInterest()).isEqualByComparingTo("8500");
        assertThat(result.eeInterest()).isEqualByComparingTo("8500.00");
    }

    // --- Tests 2-5: a single contribution's eligible-months weighting (April=11 .. March=0) ---

    @Test
    void calculate_aprilContribution_earnsElevenMonths() {
        // April is FY month 1 of 12 -> eligible months = 12-1 = 11.
        BigDecimal monthlyProduct = new BigDecimal("12000").multiply(BigDecimal.valueOf(12 - 1));

        var result = CpfInterestCalculator.calculate(monthlyProduct, BigDecimal.ZERO, BigDecimal.ZERO, RATE_8_5,
                12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        // 12000*11=132000; 132000*8.5/1200 = 935.00
        assertThat(result.totalInterest()).isEqualByComparingTo("935");
    }

    @Test
    void calculate_mayContribution_earnsTenMonths() {
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2025, 5, 15))).isEqualTo(2);
        BigDecimal monthlyProduct = new BigDecimal("12000").multiply(BigDecimal.valueOf(12 - 2));

        var result = CpfInterestCalculator.calculate(monthlyProduct, BigDecimal.ZERO, BigDecimal.ZERO, RATE_8_5,
                12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        // 12000*10=120000; 120000*8.5/1200 = 850.00
        assertThat(result.totalInterest()).isEqualByComparingTo("850");
    }

    @Test
    void calculate_februaryContribution_earnsOneMonth() {
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2026, 2, 28))).isEqualTo(11);
        BigDecimal monthlyProduct = new BigDecimal("12000").multiply(BigDecimal.valueOf(12 - 11));

        var result = CpfInterestCalculator.calculate(monthlyProduct, BigDecimal.ZERO, BigDecimal.ZERO, RATE_8_5,
                12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        // 12000*1=12000; 12000*8.5/1200 = 85.00
        assertThat(result.totalInterest()).isEqualByComparingTo("85");
    }

    @Test
    void calculate_marchContribution_earnsZeroMonthsAndZeroInterest() {
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2026, 3, 31))).isEqualTo(12);
        // March is the cutoff month itself -> eligible months = 12-12 = 0 -> contributes nothing.
        BigDecimal monthlyProduct = new BigDecimal("12000").multiply(BigDecimal.valueOf(12 - 12));

        var result = CpfInterestCalculator.calculate(monthlyProduct, BigDecimal.ZERO, BigDecimal.ZERO, RATE_8_5,
                12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        assertThat(result.totalInterest()).isEqualByComparingTo("0");
    }

    // --- Test 6: HALF_UP rounding boundary ---

    @Test
    void calculate_roundsAggregateHalfUp() {
        // Craft a monthlyProduct/rate pair whose EE interest lands exactly on 100.49 and 100.50 respectively.
        // interest = monthlyProduct * rate / 1200; pick rate=12.00 so interest = monthlyProduct/100.
        var justBelow = CpfInterestCalculator.calculate(new BigDecimal("10049"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("12.00"), 12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);
        assertThat(justBelow.totalInterest()).isEqualByComparingTo("100");

        var exactHalf = CpfInterestCalculator.calculate(new BigDecimal("10050"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("12.00"), 12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);
        assertThat(exactHalf.totalInterest()).isEqualByComparingTo("101");
    }

    // --- Tests 7-8: no intermediate/per-component rounding before aggregation ---

    @Test
    void calculate_doesNotRoundComponentsIndividuallyBeforeAggregating() {
        // Each component's own monthlyProduct is chosen so its interest is exactly 100.40 - rate 12.00% ->
        // interest = monthlyProduct/100, so monthlyProduct=10040 -> 100.40 exactly per bucket.
        BigDecimal monthlyProduct = new BigDecimal("10040");
        var result = CpfInterestCalculator.calculate(monthlyProduct, monthlyProduct, monthlyProduct,
                new BigDecimal("12.00"), 12, "2025-2026", CpfInterestCalculator.CalculationMode.FULL_YEAR);

        assertThat(result.eeInterest()).isEqualByComparingTo("100.40");
        assertThat(result.erInterest()).isEqualByComparingTo("100.40");
        assertThat(result.vpfInterest()).isEqualByComparingTo("100.40");
        // 100.40 + 100.40 + 100.40 = 301.20 -> rounds to 301, NOT 100+100+100=300 (which is what
        // rounding each component to a whole rupee first, then summing, would incorrectly give).
        assertThat(result.totalInterest()).isEqualByComparingTo("301");
    }

    // --- Structural: openingBalanceDatesFor()/monthEndsFor() shape and the March exclusion ---

    @Test
    void openingBalanceDatesFor_excludesCurrentFysOwnMarch31() {
        List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor("2025-2026");
        List<LocalDate> openingDates = CpfInterestCalculator.openingBalanceDatesFor("2025-2026");

        assertThat(monthEnds).hasSize(12);
        assertThat(openingDates).hasSize(12);
        assertThat(monthEnds.get(11)).isEqualTo(LocalDate.of(2026, 3, 31)); // the FY's own March 31
        assertThat(openingDates).doesNotContain(LocalDate.of(2026, 3, 31)); // never used as an opening-balance date
        assertThat(openingDates.get(0)).isEqualTo(LocalDate.of(2025, 3, 31)); // April's opening = preceding FY's March 31
        assertThat(openingDates.get(11)).isEqualTo(LocalDate.of(2026, 2, 28)); // March's opening = February's close
        // Each element i's closing balance (monthEnds[i]) is element i+1's opening balance.
        assertThat(openingDates.subList(1, 12)).isEqualTo(monthEnds.subList(0, 11));
    }

    @Test
    void fyMonthIndexFor_mapsAprilThroughMarchToOneThroughTwelve() {
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2025, 4, 1))).isEqualTo(1);
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2025, 12, 25))).isEqualTo(9);
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2026, 1, 1))).isEqualTo(10);
        assertThat(CpfInterestCalculator.fyMonthIndexFor(LocalDate.of(2026, 3, 31))).isEqualTo(12);
    }

    @Test
    void finYearFor_mapsJanFebMarToThePreviousAprilsFinYear() {
        assertThat(CpfInterestCalculator.finYearFor(LocalDate.of(2026, 1, 15))).isEqualTo("2025-2026");
        assertThat(CpfInterestCalculator.finYearFor(LocalDate.of(2026, 3, 31))).isEqualTo("2025-2026");
        assertThat(CpfInterestCalculator.finYearFor(LocalDate.of(2026, 4, 1))).isEqualTo("2026-2027");
    }
}
