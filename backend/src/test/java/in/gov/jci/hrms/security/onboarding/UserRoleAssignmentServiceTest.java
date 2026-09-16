package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.security.ApplicationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RBAC_SECURITY_REQUIREMENTS.md §11/§12 test matrix. */
@ExtendWith(MockitoExtension.class)
class UserRoleAssignmentServiceTest {

    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private ApplicationUserRepository userRepository;
    @Mock private AuditLogRecorder auditLogRecorder;

    private UserRoleAssignmentService service;
    private ApplicationUser targetUser;

    @BeforeEach
    void setUp() {
        service = new UserRoleAssignmentService(assignmentRepository, userRepository, auditLogRecorder);
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        targetUser = new ApplicationUser(employee, "asha.rao@jcimail.in");
        ReflectionTestUtils.setField(targetUser, "id", 200L);
        lenient().when(assignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(inv -> {
            UserRoleAssignment saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });
    }

    @Test
    void assign_newRole_createsAssignment() {
        lenient().when(userRepository.findById(200L)).thenReturn(Optional.of(targetUser));
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(200L, ApplicationRole.FIN_MAKER_CPF)).thenReturn(List.of());

        UserRoleAssignment assignment = service.assign(200L, ApplicationRole.FIN_MAKER_CPF, ScopeType.ALL_JCI, null, 999L, "grant");

        assertThat(assignment).isNotNull();
        verify(assignmentRepository).save(any(UserRoleAssignment.class));
    }

    @Test
    void assign_identicalActiveAssignmentAlreadyExists_isIdempotent_doesNotDuplicate() {
        lenient().when(userRepository.findById(200L)).thenReturn(Optional.of(targetUser));
        UserRoleAssignment existing = new UserRoleAssignment(targetUser, ApplicationRole.FIN_MAKER_CPF, ScopeType.ALL_JCI, null, 999L);
        ReflectionTestUtils.setField(existing, "id", 501L);
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(200L, ApplicationRole.FIN_MAKER_CPF)).thenReturn(List.of(existing));

        UserRoleAssignment result = service.assign(200L, ApplicationRole.FIN_MAKER_CPF, ScopeType.ALL_JCI, null, 999L, "grant");

        assertThat(result).isSameAs(existing);
        verify(assignmentRepository, never()).save(any(UserRoleAssignment.class));
    }

    @Test
    void assign_toSelf_throwsAccessDenied_selfEscalationPrevented() {
        assertThatThrownBy(() -> service.assign(200L, ApplicationRole.SYSTEM_ADMIN, ScopeType.ALL_JCI, null, 200L, "self-grant"))
                .isInstanceOf(AccessDeniedException.class);
        verify(assignmentRepository, never()).save(any(UserRoleAssignment.class));
    }

    @Test
    void revoke_bySelf_throwsAccessDenied() {
        UserRoleAssignment assignment = new UserRoleAssignment(targetUser, ApplicationRole.HR_ADMIN, ScopeType.ALL_JCI, null, 999L);
        ReflectionTestUtils.setField(assignment, "id", 500L);
        when(assignmentRepository.findById(500L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.revoke(500L, 200L, "self-revoke"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revoke_lastActiveSystemAdmin_throws() {
        UserRoleAssignment assignment = new UserRoleAssignment(targetUser, ApplicationRole.SYSTEM_ADMIN, ScopeType.ALL_JCI, null, null);
        ReflectionTestUtils.setField(assignment, "id", 500L);
        when(assignmentRepository.findById(500L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.countActiveByRoleCodeForUpdate(ApplicationRole.SYSTEM_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.revoke(500L, 999L, "removing last admin"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("last active SYSTEM_ADMIN");
        assertThat(assignment.isActive()).isTrue();
    }

    @Test
    void revoke_secondActiveSystemAdmin_succeeds() {
        UserRoleAssignment assignment = new UserRoleAssignment(targetUser, ApplicationRole.SYSTEM_ADMIN, ScopeType.ALL_JCI, null, null);
        ReflectionTestUtils.setField(assignment, "id", 500L);
        when(assignmentRepository.findById(500L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.countActiveByRoleCodeForUpdate(ApplicationRole.SYSTEM_ADMIN)).thenReturn(2L);

        service.revoke(500L, 999L, "removing one of two admins");

        assertThat(assignment.isActive()).isFalse();
    }

    @Test
    void revoke_nonSystemAdminRole_neverChecksLastAdminInvariant() {
        UserRoleAssignment assignment = new UserRoleAssignment(targetUser, ApplicationRole.HR_ADMIN, ScopeType.ALL_JCI, null, null);
        ReflectionTestUtils.setField(assignment, "id", 500L);
        when(assignmentRepository.findById(500L)).thenReturn(Optional.of(assignment));

        service.revoke(500L, 999L, "revoking HR_ADMIN");

        assertThat(assignment.isActive()).isFalse();
        verify(assignmentRepository, never()).countActiveByRoleCodeForUpdate(any());
    }
}
