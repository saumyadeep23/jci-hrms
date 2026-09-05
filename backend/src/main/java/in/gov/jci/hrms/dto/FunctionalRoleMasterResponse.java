package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.FunctionalRoleMaster;
import in.gov.jci.hrms.entity.RoleCategory;

import java.time.Instant;
import java.util.UUID;

public record FunctionalRoleMasterResponse(
        UUID id,
        String roleCode,
        String roleName,
        RoleCategory roleCategory,
        boolean hasFinancialDelegation,
        boolean hasAdministrativeDelegation,
        boolean active,
        Instant createdAt
) {
    public static FunctionalRoleMasterResponse from(FunctionalRoleMaster role) {
        return new FunctionalRoleMasterResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleCategory(),
                role.isFinancialDelegation(),
                role.isAdministrativeDelegation(),
                role.isActive(),
                role.getCreatedAt()
        );
    }
}
