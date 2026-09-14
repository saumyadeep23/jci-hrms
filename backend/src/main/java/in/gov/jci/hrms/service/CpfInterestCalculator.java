package in.gov.jci.hrms.service;

import in.gov.jci.hrms.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Single authoritative CPF interest calculation engine (EE/ER/VPF) - the ONE place the monthly
 * running-balance/product formula and its rounding rule are implemented. Every CPF interest workflow
 * delegates its actual math here rather than keeping its own copy:
 * <ul>
 *   <li>{@link CpfTrustPassbookService} - live in-year shadow-accrual projection (mode LIVE_PROJECTION)</li>
 *   <li>{@link CpfInterestRunService} - the admin-controlled Calculate/Post/Reverse workflow for year-end ANNUAL_INTEREST posting (mode FULL_YEAR)</li>
 *   <li>{@link CpfInterestComputationService#crystallizeInterimInterest} - mid-year exit/settlement interest (mode MID_YEAR_SETTLEMENT)</li>
 * </ul>
 *
 * <b>Statutory formula (Para 60, EPF Scheme 1952, as adopted here for JCI CPF - the rate itself is
 * resolved by {@link CpfRateResolutionService} from cpf_statutory_interest_rates, never hardcoded):</b>
 * simple interest per month on each month's OPENING balance (the closing balance carried in from the
 * PRECEDING month, never the closing balance including that month's own contribution), compounded only
 * annually when the year's total is credited back into the principal. For a period of {@code cutoffMonth}
 * FY-relative months (1=April..12=March; a full FY has cutoffMonth=12):
 * <pre>
 *   monthlyProduct = (openingBalance * cutoffMonth)
 *                  + sum over each month m in [1, cutoffMonth) of (that month's own net activity * (cutoffMonth - m))
 *   interest = monthlyProduct * annualRate / 1200
 * </pre>
 * A contribution posted in month m earns interest only for the (cutoffMonth - m) months remaining in the
 * period - never its own month - which is exactly why a March contribution (m=12) earns zero interest in
 * a FULL_YEAR (cutoffMonth=12) calculation: there is no 13th month within the FY for it to be the
 * "opening balance" of. This codebase computes {@code monthlyProduct} via closing-balance-as-of lookups at
 * each month's OPENING date (see {@link #openingBalanceDatesFor}) against the ledger, rather than
 * reconstructing a month-by-month activity list from scratch - that lookup-based approach is already
 * proven correct against TRANSFER_IN point-in-time joins, loan/NRW diversions and repayments, and is
 * mathematically identical to the per-month formula above (opening-balance-of-month-k = closing balance
 * after month k-1, so summing 12 opening balances is algebraically the same as the weighted-eligible-
 * months sum). This calculator is deliberately kept data-access-agnostic - callers supply the already-
 * summed monthlyProduct per bucket; see {@code CpfInterestComputationService}/{@code CpfTrustPassbookService}
 * for how those sums are accumulated.
 *
 * <b>Rounding:</b> EE/ER/VPF interest are each computed and returned at full internal precision - never
 * individually rounded before aggregation. Only the final AGGREGATE total is rounded, to the nearest whole
 * rupee (HALF_UP): e.g. 100.40 + 100.40 + 100.40 = 301.20 -> 301, not 100+100+100=300. Per-component
 * amounts are rounded only at the two-decimal-place currency boundary when a caller persists them (ledger
 * columns are NUMERIC(12,2)) - this calculator itself never truncates a component to whole rupees.
 */
final class CpfInterestCalculator {

    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");
    /** Internal working precision for per-component amounts before the caller's own storage-scale rounding - not a display scale. */
    private static final int INTERNAL_SCALE = 10;

    private CpfInterestCalculator() {
    }

    /** Which of the three CPF interest workflows a calculation is for - purely descriptive (carried on the result for logging/audit), the formula itself is identical in all three modes. */
    enum CalculationMode {
        FULL_YEAR, LIVE_PROJECTION, MID_YEAR_SETTLEMENT
    }

    /**
     * eeInterest/erInterest/vpfInterest are full-precision (never individually rounded); totalInterest is
     * their sum rounded HALF_UP to the nearest whole rupee - the only statutorily-reportable figure.
     */
    record CpfInterestCalculationResult(
            BigDecimal eeInterest,
            BigDecimal erInterest,
            BigDecimal vpfInterest,
            BigDecimal totalInterest,
            BigDecimal monthlyProductEe,
            BigDecimal monthlyProductEr,
            BigDecimal monthlyProductVpf,
            BigDecimal annualRate,
            int cutoffMonth,
            String calculationPeriod,
            CalculationMode calculationMode) {
    }

    /**
     * monthlyProductEe/Er/Vpf are each already the sum of that bucket's 12 (or fewer, for a truncated
     * period) monthly opening balances - see this class's own javadoc for how callers accumulate them.
     * cutoffMonth/calculationPeriod/calculationMode are carried through purely for the result's own
     * audit/logging value; they do not affect the arithmetic (the caller has already applied the cutoff
     * when accumulating the monthly products).
     */
    static CpfInterestCalculationResult calculate(BigDecimal monthlyProductEe, BigDecimal monthlyProductEr, BigDecimal monthlyProductVpf,
                                                   BigDecimal annualRate, int cutoffMonth, String calculationPeriod, CalculationMode calculationMode) {
        BigDecimal eeInterest = preciseInterest(monthlyProductEe, annualRate);
        BigDecimal erInterest = preciseInterest(monthlyProductEr, annualRate);
        BigDecimal vpfInterest = preciseInterest(monthlyProductVpf, annualRate);
        BigDecimal totalInterest = eeInterest.add(erInterest).add(vpfInterest).setScale(0, RoundingMode.HALF_UP);
        return new CpfInterestCalculationResult(eeInterest, erInterest, vpfInterest, totalInterest,
                monthlyProductEe, monthlyProductEr, monthlyProductVpf, annualRate, cutoffMonth, calculationPeriod, calculationMode);
    }

    /** Full precision (never rounded to whole rupees here) - zero for a non-positive monthly product, matching the pre-existing convention (a member never owes negative interest). */
    private static BigDecimal preciseInterest(BigDecimal monthlyProduct, BigDecimal annualRate) {
        if (monthlyProduct.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return monthlyProduct.multiply(annualRate).divide(TWELVE_HUNDRED, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    /** The FY-relative month index for a calendar date: April=1 .. March=12. */
    static int fyMonthIndexFor(LocalDate date) {
        int calendarMonth = date.getMonthValue();
        return calendarMonth >= 4 ? calendarMonth - 3 : calendarMonth + 9;
    }

    /** The "YYYY-YYYY" FY (April-March) that a given date falls in. */
    static String finYearFor(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    /** The 12 April-March month-end dates for a "YYYY-YYYY" finYear label, FY-chronological order. */
    static List<LocalDate> monthEndsFor(String finYear) {
        String[] parts = finYear.split("-");
        if (parts.length != 2) {
            throw new BusinessRuleViolationException("finYear must be in \"YYYY-YYYY\" form, got: " + finYear);
        }
        int startYear = Integer.parseInt(parts[0]);
        List<LocalDate> monthEnds = new ArrayList<>(12);
        YearMonth cursor = YearMonth.of(startYear, 4);
        for (int i = 0; i < 12; i++) {
            monthEnds.add(cursor.atEndOfMonth());
            cursor = cursor.plusMonths(1);
        }
        return monthEnds;
    }

    /**
     * The 12 dates whose closing balance is each FY month's OPENING balance, aligned 1:1 with
     * monthEndsFor(finYear) (element i's closing balance is the opening balance of monthEndsFor's element
     * i). Element 0 is the PRECEDING FY's March 31 (April's opening balance); each subsequent element is
     * monthEndsFor(finYear)'s own element at index i-1. The current FY's own March 31 (monthEndsFor's last
     * element) never appears here - this is precisely why a March contribution earns nothing for that FY.
     */
    static List<LocalDate> openingBalanceDatesFor(String finYear) {
        List<LocalDate> monthEnds = monthEndsFor(finYear);
        List<LocalDate> openingDates = new ArrayList<>(12);
        // April's opening balance = preceding FY's March 31 - NOT monthEnds.get(0).minusMonths(1), which
        // would land on March 30 (LocalDate#minusMonths keeps the day-of-month where possible).
        openingDates.add(YearMonth.from(monthEnds.get(0)).minusMonths(1).atEndOfMonth());
        openingDates.addAll(monthEnds.subList(0, monthEnds.size() - 1));
        return openingDates;
    }
}
