package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PUT /api/v1/payroll/trust/loans/{id}/sanction - CpfLoanApplicationService.sanctionLoan(). No
 * monthlyRecoveryPrincipal field (unlike the original field spec for this DTO): the service always
 * recomputes it as sanctionedAmount / totalInstallments, per that same spec's own "Recompute
 * monthly_recovery_principal" instruction for this step - accepting a client-supplied value here would
 * just be silently overwritten, so it's not exposed as something to submit.
 */
public record CpfLoanSanctionRequest(
        @NotNull @Positive BigDecimal sanctionedAmount,
        @NotBlank String sanctionOrderNo,
        @NotNull LocalDate sanctionDate,
        @NotNull @Min(1) Integer totalInstallments,
        /** Number of installments the total interest is spread over - separate from totalInstallments (the principal schedule) since Head 30/Head 31 recovery runs sequentially, not in parallel. */
        @NotNull @Min(1) Integer interestInstallments
) {
}
