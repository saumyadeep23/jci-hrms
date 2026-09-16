package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserRoleAssignment;

import java.time.Instant;

public record RoleAssignmentResponse(Long id, Long userId, String roleCode, ScopeType scopeType, Long scopeValue, Instant assignedAt) {

    public static RoleAssignmentResponse from(UserRoleAssignment assignment) {
        return new RoleAssignmentResponse(assignment.getId(), assignment.getUser().getId(), assignment.getRoleCode(),
                assignment.getScopeType(), assignment.getScopeValue(), assignment.getAssignedAt());
    }
}
