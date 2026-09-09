package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /api/v1/payroll/trust/incoming-transfers/{id}/credit-ledger - IncomingFundTransferService.verifyAndCreditTrustLedger(). */
public record CreditLedgerRequest(
        @NotNull Long trustOfficerId,
        String remarks
) {
}
