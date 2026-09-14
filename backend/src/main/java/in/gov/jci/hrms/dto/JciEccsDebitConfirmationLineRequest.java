package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One employee's debit outcome within a POST .../confirm-debit call (spec section 1.6/1.7).
 * {@code outcome} must be DEBIT_SUCCESS/DEBIT_PARTIAL/DEBIT_FAILED - PENDING_DEBIT/REVERSED are not
 * valid inputs here. actualDebitedAmount is required for DEBIT_PARTIAL (ignored for SUCCESS, which
 * recovers the full snapshot amount, and FAILED, which recovers zero).
 */
public record JciEccsDebitConfirmationLineRequest(
        @NotNull Long employeeId,
        @NotNull JciEccsDebitStatus outcome,
        BigDecimal actualDebitedAmount,
        String payrollTransactionId
) {
}
