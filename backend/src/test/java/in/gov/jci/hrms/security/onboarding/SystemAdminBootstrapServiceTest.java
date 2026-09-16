package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.UserAccountStatus;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.security.ApplicationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RBAC_SECURITY_REQUIREMENTS.md / ONBOARDING_SECURITY_REQUIREMENTS.md "Employee 8" bootstrap test matrix (spec section 85). Never touches a real database - Mockito-mocked repositories only (spec instruction 99). */
@ExtendWith(MockitoExtension.class)
class SystemAdminBootstrapServiceTest {

    private static final Long BOOTSTRAP_EMPLOYEE_ID = 8L;

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ApplicationUserRepository userRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private AuditLogRecorder auditLogRecorder;

    private SystemAdminBootstrapService service;
    private Employee bootstrapEmployee;

    @BeforeEach
    void setUp() {
        service = new SystemAdminBootstrapService(employeeRepository, userRepository, assignmentRepository, auditLogRecorder);
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        bootstrapEmployee = new Employee("EMP-008", "Bootstrap", "Admin", "bootstrap.admin@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(bootstrapEmployee, "id", BOOTSTRAP_EMPLOYEE_ID);
        bootstrapEmployee.setOfficialEmail("bootstrap.admin@jcimail.in");
        lenient().when(userRepository.save(any(ApplicationUser.class))).thenAnswer(inv -> {
            ApplicationUser saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 900L);
            return saved;
        });
        lenient().when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void employeeExists_withValidOfficialEmail_createsUserAndBothAssignments() {
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));
        when(userRepository.findByEmployee_Id(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.USER)).thenReturn(List.of());
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.SYSTEM_ADMIN)).thenReturn(List.of());

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        verify(userRepository).save(any(ApplicationUser.class));
        verify(assignmentRepository, org.mockito.Mockito.times(2)).save(any(UserRoleAssignment.class));
    }

    @Test
    void secondRun_doesNotDuplicateUserOrAssignments() {
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));
        ApplicationUser existing = new ApplicationUser(bootstrapEmployee, "bootstrap.admin@jcimail.in");
        ReflectionTestUtils.setField(existing, "id", 900L);
        existing.setStatus(UserAccountStatus.ACTIVE);
        when(userRepository.findByEmployee_Id(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        UserRoleAssignment existingUserRole = new UserRoleAssignment(existing, ApplicationRole.USER, in.gov.jci.hrms.entity.ScopeType.SELF, null, null);
        UserRoleAssignment existingAdminRole = new UserRoleAssignment(existing, ApplicationRole.SYSTEM_ADMIN, in.gov.jci.hrms.entity.ScopeType.ALL_JCI, null, null);
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.USER)).thenReturn(List.of(existingUserRole));
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.SYSTEM_ADMIN)).thenReturn(List.of(existingAdminRole));

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        verify(userRepository, never()).save(any(ApplicationUser.class));
        verify(assignmentRepository, never()).save(any(UserRoleAssignment.class));
    }

    @Test
    void secondRun_preservesOtherExistingAssignments() {
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));
        ApplicationUser existing = new ApplicationUser(bootstrapEmployee, "bootstrap.admin@jcimail.in");
        ReflectionTestUtils.setField(existing, "id", 900L);
        existing.setStatus(UserAccountStatus.ACTIVE);
        when(userRepository.findByEmployee_Id(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.USER)).thenReturn(List.of());
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.SYSTEM_ADMIN)).thenReturn(List.of());

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        // Bootstrap only ever queries/ensures USER and SYSTEM_ADMIN - it never touches any other role's assignments.
        verify(assignmentRepository, never()).findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.FIN_ADMIN_CPF);
    }

    @Test
    void employeeDoesNotExist_noAccountCreated_noException() {
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.empty());

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID); // must not throw

        verify(userRepository, never()).save(any(ApplicationUser.class));
        verify(assignmentRepository, never()).save(any(UserRoleAssignment.class));
    }

    @Test
    void employeeHasNoValidOfficialEmail_noAccountCreated_noUsernameInvented() {
        bootstrapEmployee.setOfficialEmail("bootstrap.admin@gmail.com");
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        verify(userRepository, never()).save(any(ApplicationUser.class));
    }

    @Test
    void employeeHasBlankOfficialEmail_noAccountCreated() {
        bootstrapEmployee.setOfficialEmail(null);
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        verify(userRepository, never()).save(any(ApplicationUser.class));
    }

    /** No `if (employeeId == 8) allowEverything()` anywhere - bootstrap is data-seeding only, unrelated to request-time authorization (spec section 39/85). */
    @Test
    void bootstrapDoesNotGrantAnyFinancialRole() {
        when(employeeRepository.findById(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.of(bootstrapEmployee));
        when(userRepository.findByEmployee_Id(BOOTSTRAP_EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.USER)).thenReturn(List.of());
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(900L, ApplicationRole.SYSTEM_ADMIN)).thenReturn(List.of());

        service.ensureBootstrapAdmin(BOOTSTRAP_EMPLOYEE_ID);

        verify(assignmentRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                a -> a != null && (a.getRoleCode().equals(ApplicationRole.FIN_ADMIN_CPF) || a.getRoleCode().equals(ApplicationRole.FIN_ADMIN_DISB)
                        || a.getRoleCode().equals(ApplicationRole.JCIECCS_ADMIN))));
    }
}
