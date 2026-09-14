package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JciEccsLoanResponse(
        Long id,
        String loanIssueId,
        Long memberId,
        String productCode,
        LocalDate sanctionDate,
        LocalDate disbursementDate,
        String disbursementCycleCode,
        BigDecimal sanctionedAmount,
        BigDecimal disbursedAmount,
        int tenureMonths,
        BigDecimal annualInterestRate,
        int monthlyPrincipalInstallment,
        BigDecimal outstandingPrincipal,
        JciEccsLoanStatus status,
        Long parentLoanId,
        int restructuringCount,
        int topupCount
) {
    public static JciEccsLoanResponse from(JciEccsLoan loan) {
        return new JciEccsLoanResponse(loan.getId(), loan.getLoanIssueId(), loan.getMember().getId(),
                loan.getLoanProduct().getProductCode().name(), loan.getSanctionDate(), loan.getDisbursementDate(),
                loan.getDisbursementCycle().getCycleCode(), loan.getSanctionedAmount(), loan.getDisbursedAmount(),
                loan.getTenureMonths(), loan.getAnnualInterestRate(), loan.getMonthlyPrincipalInstallment(),
                loan.getOutstandingPrincipal(), loan.getStatus(), loan.getParentLoan() == null ? null : loan.getParentLoan().getId(),
                loan.getRestructuringCount(), loan.getTopupCount());
    }
}
