package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLoanSettlementMode;
import in.gov.jci.hrms.entity.CpfLoanSettlementTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfLoanSettlementResponse(
        Long id,
        String receiptVoucherNo,
        Long loanId,
        String loanApplicationNo,
        Long employeeId,
        String finYear,
        CpfLoanSettlementMode settlementType,
        String instrumentOrChallanNo,
        LocalDate instrumentDate,
        LocalDate bankRealizationDate,
        String trustBankAccountCode,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalAmountPaid,
        boolean isEarlyForeclosure,
        int elapsedMonths,
        BigDecimal originalProjectedInterest,
        BigDecimal recomputedStatutoryInterest,
        BigDecimal interestRebateAmount,
        String remarks,
        Instant createdAt
) {
    public static CpfLoanSettlementResponse from(CpfLoanSettlementTransaction txn) {
        return new CpfLoanSettlementResponse(
                txn.getId(), txn.getReceiptVoucherNo(), txn.getLoan().getId(), txn.getLoan().getLoanApplicationNo(),
                txn.getEmployee().getId(), txn.getFinYear(), txn.getSettlementType(), txn.getInstrumentOrChallanNo(),
                txn.getInstrumentDate(), txn.getBankRealizationDate(), txn.getTrustBankAccountCode(),
                txn.getPrincipalPaid(), txn.getInterestPaid(), txn.getTotalAmountPaid(), txn.isEarlyForeclosure(),
                txn.getElapsedMonths(), txn.getOriginalProjectedInterest(), txn.getRecomputedStatutoryInterest(),
                txn.getInterestRebateAmount(), txn.getRemarks(), txn.getCreatedAt());
    }
}
