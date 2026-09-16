package in.gov.jci.hrms.security;

import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacSecurityTest {

    private static final String PERMISSION = "CPF_SANCTION";

    @Mock
    private UserRoleAssignmentRepository assignmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private RbacSecurity rbac;
    private ApplicationUser callerUser;

    @BeforeEach
    void setUp() {
        rbac = new RbacSecurity(assignmentRepository, employeeRepository, mockUserRepository());
        Employee callerEmployee = employeeWithId(1L, null);
        callerUser = new ApplicationUser(callerEmployee, "caller@jcimail.in");
        ReflectionTestUtils.setField(callerUser, "id", 100L);
    }

    private ApplicationUserRepository mockUserRepository() {
        ApplicationUserRepository repo = org.mockito.Mockito.mock(ApplicationUserRepository.class);
        return repo;
    }

    private JwtAuthenticationToken tokenFor(Long employeeId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("test")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .claim("employee_id", String.valueOf(employeeId)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
    }

    private UserRoleAssignment assignmentWithScope(ScopeType scopeType, Long scopeValue) {
        UserRoleAssignment assignment = new UserRoleAssignment(callerUser, "FIN_ADMIN_CPF", scopeType, scopeValue, null);
        return assignment;
    }

    private Employee employeeWithId(Long id, RegionalOffice office) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        Employee employee = new Employee("EMP-" + id, "First", "Last", "e" + id + "@example.com",
                java.time.LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", id);
        if (office != null) {
            employee.setRegionalOffice(office);
        }
        return employee;
    }

    private RegionalOffice officeWithId(Long id, OfficeType type) {
        RegionalOffice office = new RegionalOffice("RO-" + id, "Office " + id, "DL", in.gov.jci.hrms.entity.CityClass.X, true);
        ReflectionTestUtils.setField(office, "id", id);
        ReflectionTestUtils.setField(office, "officeType", type);
        return office;
    }

    @Test
    void hasPermission_noJwtEmployeeId_returnsFalse() {
        var anon = new org.springframework.security.authentication.AnonymousAuthenticationToken(
                "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(rbac.hasPermission(anon, PERMISSION)).isFalse();
    }

    @Test
    void hasPermission_noGrantingAssignment_returnsFalse() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION)).thenReturn(List.of());
        assertThat(rbac.hasPermission(tokenFor(1L), PERMISSION)).isFalse();
    }

    @Test
    void hasPermission_withAnyGrantingAssignment_returnsTrue() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.SELF, null)));
        assertThat(rbac.hasPermission(tokenFor(1L), PERMISSION)).isTrue();
    }

    @Test
    void hasPermissionInScope_allJci_alwaysCovers() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.ALL_JCI, null)));
        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 999L)).isTrue();
    }

    @Test
    void hasPermissionInScope_self_onlyCoversOwnEmployeeId() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.SELF, null)));

        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 1L)).isTrue();
        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 2L)).isFalse();
    }

    @Test
    void hasPermissionInScope_office_coversOnlyEmployeesInThatOffice() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.OFFICE, 50L)));
        RegionalOffice office50 = officeWithId(50L, OfficeType.REGIONAL_OFFICE);
        RegionalOffice office51 = officeWithId(51L, OfficeType.REGIONAL_OFFICE);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employeeWithId(2L, office50)));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeWithId(3L, office51)));

        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 2L)).isTrue();
        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 3L)).isFalse();
    }

    @Test
    void hasPermissionInScope_ho_coversOnlyHeadOfficeEmployees() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.HO, null)));
        RegionalOffice ho = officeWithId(1L, OfficeType.HEAD_OFFICE);
        RegionalOffice ro = officeWithId(2L, OfficeType.REGIONAL_OFFICE);
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employeeWithId(2L, ho)));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employeeWithId(3L, ro)));

        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 2L)).isTrue();
        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 3L)).isFalse();
    }

    /** REGION has no backing organizational entity in this schema (RbacSecurity javadoc) - always denies. */
    @Test
    void hasPermissionInScope_region_alwaysDenies() {
        when(assignmentRepository.findActiveAssignmentsGrantingPermission(1L, PERMISSION))
                .thenReturn(List.of(assignmentWithScope(ScopeType.REGION, null)));

        assertThat(rbac.hasPermissionInScope(tokenFor(1L), PERMISSION, 2L)).isFalse();
    }
}
