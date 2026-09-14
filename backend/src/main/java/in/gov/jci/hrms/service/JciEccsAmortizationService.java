package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducing-balance amortization for JCIECCS Term/Emergency loans. Principal installments are always
 * whole-rupee multiples of ₹10 (matching jcieccs_loan.monthly_principal_inst's own DB CHECK
 * chk_jcieccs_loan_inst); the final installment absorbs whatever principal remains so the loan reaches
 * exactly ₹0.00 outstanding, even though that row itself need not be a multiple of 10 (no such CHECK
 * exists on jcieccs_loan_schedule.principal_due). All BigDecimal arithmetic uses RoundingMode.HALF_UP.
 */
@Service
public class JciEccsAmortizationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final CycleResolverService cycleResolverService;

    public JciEccsAmortizationService(CycleResolverService cycleResolverService) {
        this.cycleResolverService = cycleResolverService;
    }

    /** The single standard principal installment recorded on jcieccs_loan.monthly_principal_inst -
     * nearest ₹10 multiple of principal/tenure, floored at ₹10. */
    public int computeStandardPrincipalInstallment(BigDecimal principal, int tenureMonths) {
        BigDecimal raw = principal.divide(BigDecimal.valueOf(tenureMonths), 0, RoundingMode.HALF_UP);
        BigDecimal nearestTen = raw.divide(TEN, 0, RoundingMode.HALF_UP).multiply(TEN);
        return Math.max(nearestTen.intValueExact(), 10);
    }

    /** FR-LOAN.3-style reducing-balance interest: rate applied to the opening principal of the period. */
    public BigDecimal computeMonthlyInterest(BigDecimal openingPrincipal, BigDecimal annualRatePercent) {
        return openingPrincipal.multiply(annualRatePercent)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    /**
     * Builds the full installment-by-installment schedule for an already-persisted loan, starting at
     * {@code firstCycle} (Term = disbursement cycle, Emergency = disbursement cycle + 1, resolved by the
     * caller via CycleResolverService.repaymentStartCycle before this is called). When
     * {@code doubleFirstInstallmentInterest} is true (Emergency Loan), installment 1's interest is
     * exactly 2x the normal reducing-balance figure while its principal stays the standard amount;
     * every later installment uses standard 1x interest.
     */
    public List<JciEccsLoanSchedule> generateSchedule(JciEccsLoan loan, HrmsPayrollCycle firstCycle,
                                                        boolean doubleFirstInstallmentInterest) {
        return generateSchedule(loan, 1, firstCycle, loan.getDisbursedAmount(), loan.getTenureMonths(), doubleFirstInstallmentInterest);
    }

    /**
     * The general form, also used to recalculate a loan's remaining schedule after a cash prepayment
     * (spec section 2: "Transactionally recalculates remaining schedule tenures") - resumes numbering at
     * {@code startInstallmentNo} against whatever principal/tenure is left, rather than starting a brand
     * new loan from installment 1. {@code doubleFirstInstallmentInterest} only ever applies to a real
     * installment 1 of a new Emergency Loan disbursement, never to a recalculation resume point.
     */
    public List<JciEccsLoanSchedule> generateSchedule(JciEccsLoan loan, int startInstallmentNo, HrmsPayrollCycle firstCycle,
                                                        BigDecimal startingPrincipal, int remainingInstallments,
                                                        boolean doubleFirstInstallmentInterest) {
        int standardInstallment = loan.getMonthlyPrincipalInstallment();
        BigDecimal annualRate = loan.getAnnualInterestRate();

        List<JciEccsLoanSchedule> schedule = new ArrayList<>(remainingInstallments);
        BigDecimal openingPrincipal = startingPrincipal;
        HrmsPayrollCycle cycle = firstCycle;

        for (int i = 0; i < remainingInstallments; i++) {
            int installmentNo = startInstallmentNo + i;
            boolean isFinalInstallment = i == remainingInstallments - 1;
            int principalDue = isFinalInstallment ? openingPrincipal.setScale(0, RoundingMode.HALF_UP).intValueExact() : standardInstallment;

            BigDecimal interestDue = computeMonthlyInterest(openingPrincipal, annualRate);
            if (i == 0 && doubleFirstInstallmentInterest) {
                interestDue = interestDue.multiply(TWO);
            }

            BigDecimal outstandingAfter = openingPrincipal.subtract(BigDecimal.valueOf(principalDue));
            if (isFinalInstallment) {
                outstandingAfter = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            schedule.add(new JciEccsLoanSchedule(loan, installmentNo, cycle, openingPrincipal, principalDue, interestDue, outstandingAfter));

            openingPrincipal = outstandingAfter;
            if (!isFinalInstallment) {
                YearMonth nextMonth = YearMonth.parse(cycle.getCycleCode()).plusMonths(1);
                cycle = cycleResolverService.resolveForYearMonth(nextMonth.getYear(), nextMonth.getMonthValue());
            }
        }

        return schedule;
    }
}
