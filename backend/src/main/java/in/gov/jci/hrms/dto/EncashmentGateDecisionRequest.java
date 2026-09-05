package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for both the HR (Gate 1) and Finance (Gate 2) approval endpoints - approve=false records a rejection with remarks. */
public record EncashmentGateDecisionRequest(
        @NotNull Boolean approve,
        @Size(max = 1000) String remarks
) {
}
