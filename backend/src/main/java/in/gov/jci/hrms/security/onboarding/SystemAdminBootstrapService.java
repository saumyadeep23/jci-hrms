package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserAccountStatus;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.security.ApplicationRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * RBAC_SECURITY_REQUIREMENTS.md / ONBOARDING_SECURITY_REQUIREMENTS.md "Employee 8" bootstrap.
 * Idempotent: repeated calls (every app restart, via SystemAdminBootstrapRunner) never duplicate
 * the ApplicationUser or its role assignments, and never touch any other employee's data.
 * Employee 8 must already exist - this class NEVER creates an Employee row, only an
 * ApplicationUser + role assignments for one that's already there. Never fails startup: a missing
 * employee or missing/invalid official email is logged at ERROR (a "high-visibility
 * startup/readiness diagnostic" per the task brief) and the method returns without inventing a
 * username - the rest of the application still starts and legacy JWT-role-based authorization is
 * unaffected either way. There is no `if (employeeId == bootstrapEmployeeId) allowEverything()`
 * anywhere - after this method runs once, employee 8's authority flows through the exact same
 * RbacSecurity/UserRoleAssignmentRepository path as any other user.
 */
@Service
public class SystemAdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminBootstrapService.class);

    private final EmployeeRepository employeeRepository;
    private final ApplicationUserRepository userRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final AuditLogRecorder auditLogRecorder;

    public SystemAdminBootstrapService(EmployeeRepository employeeRepository, ApplicationUserRepository userRepository,
                                        UserRoleAssignmentRepository assignmentRepository, AuditLogRecorder auditLogRecorder) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRecorder = auditLogRecorder;
    }

    @Transactional
    public void ensureBootstrapAdmin(Long bootstrapEmployeeId) {
        Optional<Employee> employeeOpt = employeeRepository.findById(bootstrapEmployeeId);
        if (employeeOpt.isEmpty()) {
            log.error("SYSTEM_ADMIN bootstrap: employee_id={} does not exist. No bootstrap admin account "
                    + "was created or modified. This must be resolved before RBAC can be administered.", bootstrapEmployeeId);
            return;
        }
        Employee employee = employeeOpt.get();
        OfficialEmailValidator.Result emailResult = OfficialEmailValidator.validate(employee.getOfficialEmail());
        if (emailResult != OfficialEmailValidator.Result.VALID) {
            log.error("SYSTEM_ADMIN bootstrap: employee_id={} has no valid official {} email ({}). "
                    + "No bootstrap admin account was created or modified - no username was invented.",
                    bootstrapEmployeeId, OfficialEmailValidator.REQUIRED_DOMAIN, emailResult);
            return;
        }

        String username = OfficialEmailValidator.normalize(employee.getOfficialEmail());
        ApplicationUser user = userRepository.findByEmployee_Id(bootstrapEmployeeId).orElse(null);
        if (user == null) {
            user = userRepository.save(new ApplicationUser(employee, username));
            user.setStatus(UserAccountStatus.ACTIVE);
            auditLogRecorder.record("ApplicationUser", user.getId(), AuditAction.CREATE, null,
                    Map.of("event", "USER_PROVISIONED", "reason", "SYSTEM_ADMIN bootstrap", "employeeId", bootstrapEmployeeId));
        } else if (user.getStatus() == UserAccountStatus.PENDING_INVITATION || user.getStatus() == UserAccountStatus.INVITED) {
            user.setStatus(UserAccountStatus.ACTIVE);
        } else if (user.getStatus() == UserAccountStatus.DISABLED || user.getStatus() == UserAccountStatus.LOCKED
                || user.getStatus() == UserAccountStatus.SEPARATED) {
            log.warn("SYSTEM_ADMIN bootstrap: employee_id={} already has an application user in status {} - "
                    + "not reactivated automatically. Role assignments below are still ensured.", bootstrapEmployeeId, user.getStatus());
        }

        ensureActiveAssignment(user, ApplicationRole.USER, ScopeType.SELF, null);
        ensureActiveAssignment(user, ApplicationRole.SYSTEM_ADMIN, ScopeType.ALL_JCI, null);
    }

    private void ensureActiveAssignment(ApplicationUser user, String roleCode, ScopeType scopeType, Long scopeValue) {
        boolean alreadyActive = assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(user.getId(), roleCode).stream()
                .anyMatch(a -> a.getScopeType() == scopeType);
        if (alreadyActive) {
            return;
        }
        UserRoleAssignment assignment = assignmentRepository.save(new UserRoleAssignment(user, roleCode, scopeType, scopeValue, null));
        auditLogRecorder.record("UserRoleAssignment", assignment.getId(), AuditAction.CREATE, null,
                Map.of("event", "ROLE_ASSIGNED", "reason", "SYSTEM_ADMIN bootstrap", "roleCode", roleCode, "scopeType", scopeType.name()));
    }
}
