package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

/** Shared rejection-remarks body - used by both CpfLoanController's and CpfTrustController's reject endpoints. */
public record RejectRemarksRequest(
        @NotBlank String remarks
) {
}
