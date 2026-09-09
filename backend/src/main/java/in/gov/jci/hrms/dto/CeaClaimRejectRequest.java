package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CeaClaimRejectRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
