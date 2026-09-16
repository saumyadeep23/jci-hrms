package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.RoleAssignmentRequest;
import in.gov.jci.hrms.dto.RoleAssignmentResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.onboarding.UserRoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RBAC_SECURITY_REQUIREMENTS.md §12 - role assignment authority, gated by USER_ROLE_ASSIGN (SYSTEM_ADMIN only in the V94 seed). */
@RestController
@RequestMapping("/api/admin/users/roles")
@PreAuthorize("@rbac.hasPermission(authentication, 'USER_ROLE_ASSIGN')")
public class AdminUserRoleController {

    private final UserRoleAssignmentService assignmentService;
    private final RbacSecurity rbac;

    public AdminUserRoleController(UserRoleAssignmentService assignmentService, RbacSecurity rbac) {
        this.assignmentService = assignmentService;
        this.rbac = rbac;
    }

    @PostMapping
    public ResponseEntity<RoleAssignmentResponse> assign(@Valid @RequestBody RoleAssignmentRequest request, Authentication authentication) {
        Long actingUserId = requireCurrentUserId(authentication);
        var assignment = assignmentService.assign(request.userId(), request.roleCode(), request.scopeType(),
                request.scopeValue(), actingUserId, request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleAssignmentResponse.from(assignment));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> revoke(@PathVariable Long assignmentId, Authentication authentication) {
        Long actingUserId = requireCurrentUserId(authentication);
        assignmentService.revoke(assignmentId, actingUserId, null);
        return ResponseEntity.noContent().build();
    }

    private Long requireCurrentUserId(Authentication authentication) {
        Long userId = rbac.resolveCurrentUserId(authentication);
        if (userId == null) {
            throw new BusinessRuleViolationException("Caller has no application user account - cannot attribute this action");
        }
        return userId;
    }
}
