package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.security.ApplicationRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * RBAC_SECURITY_REQUIREMENTS.md §12 (role assignment security) and §11 (last-SYSTEM_ADMIN
 * invariant). Permission to call this at all (RbacPermission.USER_ROLE_ASSIGN) is enforced at the
 * controller layer; this service enforces the business invariants that hold regardless of who is
 * calling.
 */
@Service
@Transactional(readOnly = true)
public class UserRoleAssignmentService {

    private final UserRoleAssignmentRepository assignmentRepository;
    private final ApplicationUserRepository userRepository;
    private final AuditLogRecorder auditLogRecorder;

    public UserRoleAssignmentService(UserRoleAssignmentRepository assignmentRepository, ApplicationUserRepository userRepository,
                                      AuditLogRecorder auditLogRecorder) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.auditLogRecorder = auditLogRecorder;
    }

    @Transactional
    public UserRoleAssignment assign(Long targetUserId, String roleCode, ScopeType scopeType, Long scopeValue, Long actingUserId, String reason) {
        requireNotSelf(targetUserId, actingUserId, "assign a role to themselves");
        ApplicationUser targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new MasterDataNotFoundException("Application User", targetUserId));

        // Idempotent (RBAC_SECURITY_REQUIREMENTS.md §15/instruction 75): an identical already-active
        // assignment is returned as-is rather than duplicated. uq_user_role_assignments_active
        // (V94) is the concurrency-safe backstop if two such calls race.
        List<UserRoleAssignment> existing = assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(targetUserId, roleCode);
        for (UserRoleAssignment candidate : existing) {
            if (candidate.getScopeType() == scopeType
                    && (candidate.getScopeValue() == null ? scopeValue == null : candidate.getScopeValue().equals(scopeValue))) {
                return candidate;
            }
        }

        UserRoleAssignment assignment = assignmentRepository.save(
                new UserRoleAssignment(targetUser, roleCode, scopeType, scopeValue, actingUserId));
        auditLogRecorder.record("UserRoleAssignment", assignment.getId(), AuditAction.CREATE, null,
                Map.of("event", "ROLE_ASSIGNED", "targetUserId", targetUserId, "roleCode", roleCode,
                        "scopeType", scopeType.name(), "assignedBy", actingUserId));
        return assignment;
    }

    @Transactional
    public void revoke(Long assignmentId, Long actingUserId, String reason) {
        UserRoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new MasterDataNotFoundException("User Role Assignment", assignmentId));
        requireNotSelf(assignment.getUser().getId(), actingUserId, "revoke their own role");
        if (!assignment.isActive()) {
            throw new BusinessRuleViolationException("User role assignment " + assignmentId + " is already revoked");
        }
        if (ApplicationRole.SYSTEM_ADMIN.equals(assignment.getRoleCode())) {
            // Row-locked count (see repository javadoc) - genuinely concurrency-safe, not just a
            // check-then-update race (RBAC_SECURITY_REQUIREMENTS.md §11).
            long activeSystemAdmins = assignmentRepository.countActiveByRoleCodeForUpdate(ApplicationRole.SYSTEM_ADMIN);
            if (activeSystemAdmins <= 1) {
                throw new BusinessRuleViolationException("Cannot revoke the last active SYSTEM_ADMIN assignment");
            }
        }
        assignment.revoke(actingUserId, reason);
        auditLogRecorder.record("UserRoleAssignment", assignmentId, AuditAction.UPDATE, null,
                Map.of("event", "ROLE_REVOKED", "revokedBy", actingUserId));
    }

    private void requireNotSelf(Long targetUserId, Long actingUserId, String action) {
        if (actingUserId != null && actingUserId.equals(targetUserId)) {
            throw new AccessDeniedException("A user may not " + action + " - self-escalation/self-revocation is not permitted");
        }
    }
}
