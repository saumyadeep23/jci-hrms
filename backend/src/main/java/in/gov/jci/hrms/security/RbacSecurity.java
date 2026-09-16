package in.gov.jci.hrms.security;

import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Centralized DB-backed permission/scope evaluator (RBAC_SECURITY_REQUIREMENTS.md "centralized
 * scope evaluation" requirement) - generalizes this codebase's existing isSelf pattern
 * (EmployeeSecurity, AttendanceAggregationSecurity, etc., all still in place and unchanged) from
 * "same employee or a hardcoded admin role" to "does this employee's DB-backed role/permission
 * grant reach this target, in this scope."
 *
 * <p>Deliberately independent of JWT role claims (JwtRoleConverter): per
 * RBAC_SECURITY_REQUIREMENTS.md's "OIDC claim trust" requirement, HRMS authorization must come
 * from the application's own role/permission model, not be inferrable from arbitrary IdP token
 * claims. Every check here resolves the caller's identity only as far as their JWT `employee_id`
 * claim (SecurityUtils.currentEmployeeId) and then looks up DB-backed roles/scopes from there -
 * an attacker who could forge role claims (see SEC-001) still could not forge a
 * user_role_assignments row.
 *
 * <p>Scope resolution: SELF and ALL_JCI are fully general. OFFICE compares the target employee's
 * Employee.regionalOffice.id against the assignment's scope_value. HO grants access to any
 * employee whose own regionalOffice.officeType = HEAD_OFFICE (RegionalOffice is the only
 * org-unit tier in this schema - see ScopeType javadoc). REGION has no backing organizational
 * entity in the current schema (no separate "Region" grouping of RegionalOffices exists -
 * confirmed by inspecting Employee/RegionalOffice/OfficeType before implementing this class) and
 * therefore always denies rather than silently granting broader access; a REGION-scoped
 * assignment is data-model-valid (so it can be created once real region data exists) but
 * operationally inert today. This is documented in docs/security/RBAC_MIGRATION_REPORT.md as
 * REQUIRES_BUSINESS_CONFIRMATION.
 */
@Component("rbac")
public class RbacSecurity {

    private final UserRoleAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationUserRepository userRepository;

    public RbacSecurity(UserRoleAssignmentRepository assignmentRepository, EmployeeRepository employeeRepository,
                         ApplicationUserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    /**
     * Phase D (RBAC_SECURITY_REQUIREMENTS.md "authenticated user resolution"): the one place that
     * maps a JWT -> ApplicationUser id. Controllers needing to attribute an action (initiatedBy,
     * assignedBy, etc.) call this rather than re-deriving it themselves. Returns null if the
     * caller's employee has no ApplicationUser row yet (not yet onboarded).
     */
    public Long resolveCurrentUserId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            return null;
        }
        return userRepository.findByEmployee_Id(employeeId).map(ApplicationUser::getId).orElse(null);
    }

    /** Does the caller hold ANY active role granting this permission, regardless of scope? */
    public boolean hasPermission(Authentication authentication, String permissionCode) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            return false;
        }
        return !assignmentRepository.findActiveAssignmentsGrantingPermission(employeeId, permissionCode).isEmpty();
    }

    /** Does the caller hold an active role granting this permission, in a scope that covers targetEmployeeId? */
    public boolean hasPermissionInScope(Authentication authentication, String permissionCode, Long targetEmployeeId) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        if (callerEmployeeId == null || targetEmployeeId == null) {
            return false;
        }
        List<UserRoleAssignment> grants = assignmentRepository.findActiveAssignmentsGrantingPermission(callerEmployeeId, permissionCode);
        for (UserRoleAssignment grant : grants) {
            if (scopeCovers(grant, callerEmployeeId, targetEmployeeId)) {
                return true;
            }
        }
        return false;
    }

    private boolean scopeCovers(UserRoleAssignment grant, Long callerEmployeeId, Long targetEmployeeId) {
        return switch (grant.getScopeType()) {
            case ALL_JCI -> true;
            case SELF -> callerEmployeeId.equals(targetEmployeeId);
            case OFFICE -> grant.getScopeValue() != null && officeMatches(grant.getScopeValue(), targetEmployeeId);
            case HO -> targetIsHeadOffice(targetEmployeeId);
            case REGION -> false;
        };
    }

    private boolean officeMatches(Long officeId, Long targetEmployeeId) {
        return employeeRepository.findById(targetEmployeeId)
                .map(Employee::getRegionalOffice)
                .map(RegionalOffice::getId)
                .map(officeId::equals)
                .orElse(false);
    }

    private boolean targetIsHeadOffice(Long targetEmployeeId) {
        return employeeRepository.findById(targetEmployeeId)
                .map(Employee::getRegionalOffice)
                .map(RegionalOffice::getOfficeType)
                .map(type -> type == OfficeType.HEAD_OFFICE)
                .orElse(false);
    }
}
