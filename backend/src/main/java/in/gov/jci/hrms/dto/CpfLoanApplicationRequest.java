package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLoanType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** POST /api/v1/payroll/trust/loans/apply - CpfLoanApplicationService.applyLoan(). */
public record CpfLoanApplicationRequest(
        @NotNull Long employeeId,
        @NotNull CpfLoanType loanType,
        /** Free text, but the frontend offers a fixed set (HOUSING/MEDICAL/MARRIAGE/EDUCATION/SPECIAL) - not DB-constrained, matching the live cpf_loan_applications.purpose column. */
        @NotBlank String purpose,
        @NotNull @Positive BigDecimal appliedAmount,
        @NotNull @Min(1) Integer totalInstallments,
        String reason
) {
}
