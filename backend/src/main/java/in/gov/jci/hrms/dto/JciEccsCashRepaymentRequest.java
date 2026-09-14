package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/jcieccs/loans/{loanId}/repayments, source = CASH - an outside-payroll deposit. At least
 * one of principalAmount/interestAmount must be positive (mirrors jcieccs_loan_repayment's own DB
 * CHECK). idempotencyKey (Phase 1) is caller-supplied (e.g. the cashier UI generates one per receipt
 * form) and is required so a retried/duplicated submission (network retry, double-click) resolves to the
 * original recovery instead of posting the deposit twice - see JciEccsRecoveryRepository.findByIdempotencyKey. */
public record JciEccsCashRepaymentRequest(
        @NotNull @PositiveOrZero BigDecimal principalAmount,
        @NotNull @PositiveOrZero BigDecimal interestAmount,
        @NotNull LocalDate repaymentDate,
        String referenceId,
        @NotBlank String idempotencyKey
) {
}
