package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfLoanApplicationResponse(
        Long id,
        String loanApplicationNo,
        Long employeeId,
        String employeeCode,
        String employeeName,
        CpfLoanType loanType,
        String purpose,
        String applicationReason,
        BigDecimal appliedAmount,
        BigDecimal sanctionedAmount,
        String sanctionOrderNo,
        LocalDate sanctionDate,
        BigDecimal baseCpfRate,
        BigDecimal interestRate,
        BigDecimal totalInterestAmount,
        BigDecimal monthlyRecoveryPrincipal,
        BigDecimal monthlyRecoveryInterest,
        int totalInstallments,
        int recoveredInstallments,
        int totalInterestInstallments,
        int recoveredInterestInstallments,
        BigDecimal outstandingBalance,
        BigDecimal outstandingInterest,
        CpfLoanRecoveryPhase recoveryPhase,
        boolean isPreclosed,
        Instant preclosedAt,
        CpfLoanApplicationStatus status,
        String rejectionRemarks,
        Instant disbursedAt,
        Instant createdAt
) {
    public static CpfLoanApplicationResponse from(CpfLoanApplication loan) {
        return new CpfLoanApplicationResponse(
                loan.getId(), loan.getLoanApplicationNo(), loan.getEmployee().getId(), loan.getEmployee().getEmployeeCode(),
                loan.getEmployee().getFullName(), loan.getLoanType(), loan.getPurpose(), loan.getApplicationReason(),
                loan.getAppliedAmount(), loan.getSanctionedAmount(), loan.getSanctionOrderNo(), loan.getSanctionDate(),
                loan.getBaseCpfRate(), loan.getInterestRate(), loan.getTotalInterestAmount(),
                loan.getMonthlyRecoveryPrincipal(), loan.getMonthlyRecoveryInterest(), loan.getTotalInstallments(),
                loan.getRecoveredInstallments(), loan.getTotalInterestInstallments(), loan.getRecoveredInterestInstallments(),
                loan.getOutstandingBalance(), loan.getOutstandingInterest(), loan.getRecoveryPhase(), loan.isPreclosed(), loan.getPreclosedAt(),
                loan.getStatus(), loan.getRejectionRemarks(), loan.getDisbursedAt(), loan.getCreatedAt());
    }
}
