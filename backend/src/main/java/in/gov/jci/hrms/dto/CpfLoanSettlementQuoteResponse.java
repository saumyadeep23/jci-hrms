package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/payroll/trust/loans/{id}/settlement-quote - CpfLoanSettlementService.calculateEarlySettlementQuote().
 * netPayoffAmount is what a member would need to pay today to fully close the loan out-of-payroll, factoring
 * in the interest rebate earned by not running the loan's full original tenure.
 */
public record CpfLoanSettlementQuoteResponse(
        Long loanId,
        int elapsedMonths,
        BigDecimal outstandingBalance,
        BigDecimal outstandingInterest,
        BigDecimal originalProjectedInterest,
        BigDecimal recomputedStatutoryInterest,
        BigDecimal interestRebateAmount,
        BigDecimal netPayoffAmount
) {
}
