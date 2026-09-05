package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoiningDecisionRequest(
        @NotNull Boolean approve,
        @Size(max = 1000) String remarks
) {
}
