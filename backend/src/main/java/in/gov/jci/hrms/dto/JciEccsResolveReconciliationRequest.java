package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsReconciliationResolutionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST /api/jcieccs/reconciliation/{id}/resolve - remarks are mandatory (spec section 13). */
public record JciEccsResolveReconciliationRequest(
        @NotNull JciEccsReconciliationResolutionAction action,
        @NotBlank String remarks
) {
}
