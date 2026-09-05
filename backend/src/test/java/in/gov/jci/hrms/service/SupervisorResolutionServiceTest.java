package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFunctionalRoleAssignment;
import in.gov.jci.hrms.entity.FunctionalRoleMaster;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RoleCategory;
import in.gov.jci.hrms.repository.EmployeeFunctionalRoleAssignmentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.service.SupervisorResolutionService.SupervisorResolution;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorResolutionServiceTest {

    @Mock
    private PostIncumbencyRepository postIncumbencyRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeFunctionalRoleAssignmentRepository functionalRoleAssignmentRepository;

    private SupervisorResolutionService supervisorResolutionService;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        supervisorResolutionService = new SupervisorResolutionService(postIncumbencyRepository, employeeRepository,
                functionalRoleAssignmentRepository);
        department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 1L);
        designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 2L);
    }

    private PostMaster post(Long id, PostMaster reportingPost) {
        PostMaster post = new PostMaster("PC-" + id, "Post " + id, department, designation, true);
        ReflectionTestUtils.setField(post, "id", id);
        post.setOperationalReportingPost(reportingPost);
        return post;
    }

    private Employee employee(Long id, String code) {
        Employee employee = new Employee(code, "First" + id, "Last" + id, code.toLowerCase() + "@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private PostIncumbency incumbency(PostMaster post, Employee employee, AssignmentType type) {
        return new PostIncumbency(post, employee, type, LocalDate.of(2024, 1, 1));
    }

    @Test
    void resolveSupervisor_walksOneHopToImmediateReportingPost() {
        PostMaster managerPost = post(2L, null);
        PostMaster staffPost = post(1L, managerPost);

        Employee staff = employee(10L, "EMP-010");
        Employee manager = employee(20L, "EMP-020");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(10L))
                .thenReturn(List.of(incumbency(staffPost, staff, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L))
                .thenReturn(List.of(incumbency(managerPost, manager, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-020");
        assertThat(resolved.get().post().getId()).isEqualTo(2L);
    }

    @Test
    void resolveSupervisor_skipsVacantIntermediatePostsUntilAnOccupiedOneIsFound() {
        PostMaster topPost = post(3L, null);
        PostMaster vacantMiddlePost = post(2L, topPost);
        PostMaster staffPost = post(1L, vacantMiddlePost);

        Employee staff = employee(10L, "EMP-010");
        Employee director = employee(30L, "EMP-030");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(10L))
                .thenReturn(List.of(incumbency(staffPost, staff, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L)).thenReturn(List.of());
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(3L))
                .thenReturn(List.of(incumbency(topPost, director, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-030");
    }

    @Test
    void resolveSupervisor_ignoresActingIncumbentAtReportingPost_prefersSubstantive() {
        PostMaster managerPost = post(2L, null);
        PostMaster staffPost = post(1L, managerPost);

        Employee staff = employee(10L, "EMP-010");
        Employee actingHolder = employee(21L, "EMP-021");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(10L))
                .thenReturn(List.of(incumbency(staffPost, staff, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L))
                .thenReturn(List.of(incumbency(managerPost, actingHolder, AssignmentType.ACTING)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveSupervisor_whenNoReportingPostSet_returnsEmpty() {
        PostMaster topPost = post(1L, null);
        Employee director = employee(30L, "EMP-030");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(30L))
                .thenReturn(List.of(incumbency(topPost, director, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(30L);

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveSupervisor_whenEmployeeHasNoActivePost_returnsEmpty() {
        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(99L)).thenReturn(List.of());

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(99L);

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveSupervisor_withCyclicReportingChain_terminatesWithoutHangingAndReturnsEmpty() {
        PostMaster postA = post(1L, null);
        PostMaster postB = post(2L, postA);
        postA.setOperationalReportingPost(postB); // A -> B -> A cycle

        Employee staff = employee(10L, "EMP-010");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(10L))
                .thenReturn(List.of(incumbency(postA, staff, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L)).thenReturn(List.of());

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolveSupervisor_fallsBackToNonSubstantiveCurrentPost_whenNoSubstantiveIncumbencyExists() {
        PostMaster managerPost = post(2L, null);
        PostMaster staffPost = post(1L, managerPost);

        Employee staffOnActingCharge = employee(11L, "EMP-011");
        Employee manager = employee(20L, "EMP-020");

        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(11L))
                .thenReturn(List.of(incumbency(staffPost, staffOnActingCharge, AssignmentType.ACTING)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L))
                .thenReturn(List.of(incumbency(managerPost, manager, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(11L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-020");
    }

    private EmployeeFunctionalRoleAssignment hodAssignment(Employee hod) {
        FunctionalRoleMaster hodRole = new FunctionalRoleMaster("HOD", "Head of Department", RoleCategory.FUNCTIONAL);
        return new EmployeeFunctionalRoleAssignment(hodRole, hod, "JCI/HO/Pers/2026/112",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
    }

    @Test
    void resolveSupervisor_routesDirectlyToFunctionalHod_whenActiveAssignmentExistsForApplicantsDepartment() {
        Employee applicant = employee(10L, "EMP-010");
        Employee hod = employee(40L, "EMP-040");

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(applicant));
        when(functionalRoleAssignmentRepository.findActiveByRoleCodeAndDepartment(eq("HOD"), eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(hodAssignment(hod)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-040");
        assertThat(resolved.get().post()).isNull();
    }

    @Test
    void resolveSupervisor_skipsFunctionalHodAssignment_whenApplicantIsTheHodThemselves() {
        Employee applicant = employee(40L, "EMP-040");
        PostMaster managerPost = post(2L, null);
        PostMaster staffPost = post(1L, managerPost);
        Employee manager = employee(20L, "EMP-020");

        when(employeeRepository.findById(40L)).thenReturn(Optional.of(applicant));
        when(functionalRoleAssignmentRepository.findActiveByRoleCodeAndDepartment(eq("HOD"), eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(hodAssignment(applicant)));
        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(40L))
                .thenReturn(List.of(incumbency(staffPost, applicant, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L))
                .thenReturn(List.of(incumbency(managerPost, manager, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(40L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-020");
    }

    @Test
    void resolveSupervisor_fallsBackToSubstantiveReportingPost_whenNoHodAssignmentIsConfigured() {
        Employee applicant = employee(10L, "EMP-010");
        PostMaster managerPost = post(2L, null);
        PostMaster staffPost = post(1L, managerPost);
        Employee manager = employee(20L, "EMP-020");

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(applicant));
        when(functionalRoleAssignmentRepository.findActiveByRoleCodeAndDepartment(eq("HOD"), eq(1L), any(LocalDate.class)))
                .thenReturn(List.of());
        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(10L))
                .thenReturn(List.of(incumbency(staffPost, applicant, AssignmentType.SUBSTANTIVE)));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(2L))
                .thenReturn(List.of(incumbency(managerPost, manager, AssignmentType.SUBSTANTIVE)));

        Optional<SupervisorResolution> resolved = supervisorResolutionService.resolveSupervisor(10L);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().employee().getEmployeeCode()).isEqualTo("EMP-020");
        assertThat(resolved.get().post().getId()).isEqualTo(2L);
    }
}
