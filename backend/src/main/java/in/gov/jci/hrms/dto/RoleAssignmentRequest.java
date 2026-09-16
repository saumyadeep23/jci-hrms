package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleAssignmentRequest(
        @NotNull Long userId,
        @NotBlank String roleCode,
        @NotNull ScopeType scopeType,
        Long scopeValue,
        @Size(max = 500) String reason
) {
}
