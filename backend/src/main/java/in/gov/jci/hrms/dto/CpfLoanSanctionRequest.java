package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
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
 *
 * sancNrwEe/Er/Vpf are required (and must sum to sanctionedAmount) only when sanctioning a
 * NON_REFUNDABLE_WITHDRAWAL - the officer allocates the withdrawal across the three funds it is drawn
 * from. Ignored for a REFUNDABLE_LOAN, which always debits EE only (spilling into VPF if EE is
 * insufficient) rather than an officer-chosen split - see CpfLoanApplicationService.disburseLoan().
 */
public record CpfLoanSanctionRequest(
        @NotNull @Positive BigDecimal sanctionedAmount,
        @NotBlank String sanctionOrderNo,
        @NotNull LocalDate sanctionDate,
        @NotNull @Min(1) Integer totalInstallments,
        /**
         * Number of installments the total interest is spread over - separate from totalInstallments
         * (the principal schedule) since Head 30/Head 31 recovery runs sequentially, not in parallel.
         * Optional: when omitted, CpfLoanApplicationService derives it from the currently-effective
         * CpfLoanRecoveryPolicy.principalInstallmentsPerInterestInstallment (Part 19) rather than
         * requiring the sanctioning officer to already know/hardcode the ratio. An explicit value here
         * is still honored as an officer override.
         */
        @Min(1) Integer interestInstallments,
        @DecimalMin("0.0") BigDecimal sancNrwEe,
        @DecimalMin("0.0") BigDecimal sancNrwEr,
        @DecimalMin("0.0") BigDecimal sancNrwVpf
) {
}
