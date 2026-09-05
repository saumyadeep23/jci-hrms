package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoaResolverServiceTest {

    @Mock
    private PostIncumbencyRepository postIncumbencyRepository;
    @Mock
    private PostMasterRepository postMasterRepository;

    private DoaResolverService service;
    private Department department;
    private Designation designation;
    private Employee employee;
    private Employee supervisorEmployee;
    private PostMaster employeePost;
    private PostMaster reportingPost;

    @BeforeEach
    void setUp() {
        service = new DoaResolverService(postIncumbencyRepository, postMasterRepository);

        department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);

        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        supervisorEmployee = new Employee("EMP-002", "Vikram", "Shah", "vikram.shah@example.com", LocalDate.of(2010, 1, 1), department, designation);
        ReflectionTestUtils.setField(supervisorEmployee, "id", 2L);

        employeePost = new PostMaster("PC-001", "Engineer", department, designation, true);
        ReflectionTestUtils.setField(employeePost, "id", 100L);
        reportingPost = new PostMaster("PC-002", "Chief Engineer", department, designation, true);
        ReflectionTestUtils.setField(reportingPost, "id", 101L);
        employeePost.setOperationalReportingPost(reportingPost);

        PostIncumbency employeeIncumbency = new PostIncumbency(employeePost, employee, AssignmentType.SUBSTANTIVE, LocalDate.of(2020, 1, 1));
        lenient().when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(1L)).thenReturn(List.of(employeeIncumbency));
        lenient().when(postMasterRepository.findByTitleContainingIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
    }

    @Test
    void resolveApprover_prefersSubstantiveHolderAtReportingPost() {
        PostIncumbency substantive = new PostIncumbency(reportingPost, supervisorEmployee, AssignmentType.SUBSTANTIVE, LocalDate.of(2020, 1, 1));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(101L)).thenReturn(List.of(substantive));

        Optional<DoaResolverService.DoaResolution> resolution = service.resolveApprover(1L);

        assertThat(resolution).isPresent();
        assertThat(resolution.get().employee().getId()).isEqualTo(2L);
        assertThat(resolution.get().routingReason()).contains("Substantive");
    }

    @Test
    void resolveApprover_fallsBackToAdditionalChargeWhenNoSubstantiveHolder() {
        PostIncumbency additionalCharge = new PostIncumbency(reportingPost, supervisorEmployee, AssignmentType.ADDITIONAL_CHARGE, LocalDate.of(2020, 1, 1));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(101L)).thenReturn(List.of(additionalCharge));

        Optional<DoaResolverService.DoaResolution> resolution = service.resolveApprover(1L);

        assertThat(resolution).isPresent();
        assertThat(resolution.get().employee().getId()).isEqualTo(2L);
        assertThat(resolution.get().routingReason()).contains("Additional Charge");
    }

    @Test
    void resolveApprover_ignoresLookAfterAndActingHolders() {
        PostIncumbency lookAfter = new PostIncumbency(reportingPost, supervisorEmployee, AssignmentType.LOOK_AFTER, LocalDate.of(2020, 1, 1));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(101L)).thenReturn(List.of(lookAfter));
        // No further reporting post configured beyond reportingPost, so resolution should end up empty.

        Optional<DoaResolverService.DoaResolution> resolution = service.resolveApprover(1L);

        assertThat(resolution).isEmpty();
    }

    @Test
    void resolveApprover_gradeE4Title_routesToManagingDirectorWhenPostExists() {
        Designation seniorDesignation = new Designation("Senior Manager (E4)");
        ReflectionTestUtils.setField(seniorDesignation, "id", 21L);
        PostMaster seniorPost = new PostMaster("PC-003", "Senior Manager (E4)", department, seniorDesignation, true);
        ReflectionTestUtils.setField(seniorPost, "id", 102L);
        PostIncumbency seniorIncumbency = new PostIncumbency(seniorPost, employee, AssignmentType.SUBSTANTIVE, LocalDate.of(2020, 1, 1));
        when(postIncumbencyRepository.findByEmployeeIdAndActiveTrue(1L)).thenReturn(List.of(seniorIncumbency));

        PostMaster mdPost = new PostMaster("PC-MD", "Managing Director", department, designation, true);
        ReflectionTestUtils.setField(mdPost, "id", 200L);
        when(postMasterRepository.findByTitleContainingIgnoreCase("Managing Director")).thenReturn(List.of(mdPost));
        PostIncumbency mdIncumbency = new PostIncumbency(mdPost, supervisorEmployee, AssignmentType.SUBSTANTIVE, LocalDate.of(2018, 1, 1));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(200L)).thenReturn(List.of(mdIncumbency));

        Optional<DoaResolverService.DoaResolution> resolution = service.resolveApprover(1L);

        assertThat(resolution).isPresent();
        assertThat(resolution.get().post().getId()).isEqualTo(200L);
        assertThat(resolution.get().routingReason()).contains("MD");
    }
}
