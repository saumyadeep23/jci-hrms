package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EmployeeLoanResponse(
        Long id,
        Long loanTypeId,
        String loanTypeName,
        Long employeeId,
        String employeeCode,
        String loanAccountNumber,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        Integer totalInstallments,
        Integer remainingInstallments,
        BigDecimal outstandingPrincipal,
        LoanStatus status,
        LocalDate sanctionDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeLoanResponse from(EmployeeLoan loan) {
        return new EmployeeLoanResponse(
                loan.getId(),
                loan.getLoanType().getId(),
                loan.getLoanType().getName(),
                loan.getEmployee().getId(),
                loan.getEmployee().getEmployeeCode(),
                loan.getLoanAccountNumber(),
                loan.getPrincipalAmount(),
                loan.getInterestRate(),
                loan.getTotalInstallments(),
                loan.getRemainingInstallments(),
                loan.getOutstandingPrincipal(),
                loan.getStatus(),
                loan.getSanctionDate(),
                loan.getCreatedAt(),
                loan.getUpdatedAt()
        );
    }
}
