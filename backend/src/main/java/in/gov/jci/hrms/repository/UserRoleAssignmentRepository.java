package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    List<UserRoleAssignment> findByUser_IdAndRevokedAtIsNull(Long userId);

    List<UserRoleAssignment> findByUser_IdAndRoleCodeAndRevokedAtIsNull(Long userId, String roleCode);

    /** Global count, not per-user - backs the last-SYSTEM_ADMIN invariant (RBAC_SECURITY_REQUIREMENTS.md). */
    long countByRoleCodeAndRevokedAtIsNull(String roleCode);

    /**
     * Same count, but row-locked first (same FOR-UPDATE-subquery pattern this codebase already
     * uses for financial concurrency, e.g. JciEccsRecoveryService) - serializes concurrent
     * "revoke the last SYSTEM_ADMIN" attempts so the second transaction sees the first's committed
     * revocation before deciding, rather than both reading "2 active" and both proceeding.
     */
    @Query(value = "SELECT COUNT(*) FROM (SELECT id FROM user_role_assignments "
            + "WHERE role_code = :roleCode AND revoked_at IS NULL FOR UPDATE) locked_rows", nativeQuery = true)
    long countActiveByRoleCodeForUpdate(@Param("roleCode") String roleCode);

    /**
     * Centralized permission/scope evaluation (RbacSecurity, generalizing the existing isSelf
     * pattern per RBAC_SECURITY_REQUIREMENTS.md's "centralized scope evaluation" requirement):
     * every currently-active assignment for this employee whose role grants the given permission,
     * so the caller can then check each assignment's scope_type/scope_value against the target.
     */
    @Query(value = "SELECT ura.* FROM user_role_assignments ura "
            + "JOIN application_users au ON au.id = ura.user_id "
            + "JOIN role_permissions rp ON rp.role_code = ura.role_code "
            + "WHERE au.employee_id = :employeeId AND ura.revoked_at IS NULL AND rp.permission_code = :permissionCode",
            nativeQuery = true)
    List<UserRoleAssignment> findActiveAssignmentsGrantingPermission(@Param("employeeId") Long employeeId,
                                                                      @Param("permissionCode") String permissionCode);
}
