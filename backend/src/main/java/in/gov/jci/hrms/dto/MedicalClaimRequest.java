package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MedicalClaimRequest(
        @NotBlank @Size(max = 50) String claimNumber,
        @NotNull Long employeeId,
        Long dependentId,
        boolean contractOverride,
        @NotEmpty List<@Valid MedicalClaimItemRequest> items
) {
}
