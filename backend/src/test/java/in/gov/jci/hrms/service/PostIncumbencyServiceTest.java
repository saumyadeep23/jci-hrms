package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostIncumbencyServiceTest {

    private static final Long POST_ID = 1L;
    private static final Long EMPLOYEE_ID = 2L;

    @Mock
    private PostIncumbencyRepository postIncumbencyRepository;
    @Mock
    private PostMasterRepository postMasterRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private PostIncumbencyService postIncumbencyService;

    private PostMaster post;
    private Employee employee;

    @BeforeEach
    void setUp() {
        postIncumbencyService = new PostIncumbencyService(postIncumbencyRepository, postMasterRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);

        post = new PostMaster("PC-001", "Chief Engineer", department, designation, true);
        ReflectionTestUtils.setField(post, "id", POST_ID);

        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private PostIncumbencyRequest validRequest(AssignmentType type, LocalDate startDate) {
        return new PostIncumbencyRequest(POST_ID, EMPLOYEE_ID, type, startDate, null, "ORD/2026/001");
    }

    @Test
    void create_substantiveWithNoPriorIncumbent_savesWithoutClosingAnything() {
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(POST_ID)).thenReturn(List.of());
        when(postIncumbencyRepository.saveAndFlush(any(PostIncumbency.class))).thenAnswer(inv -> {
            PostIncumbency saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        PostIncumbencyResponse response = postIncumbencyService.create(validRequest(AssignmentType.SUBSTANTIVE, LocalDate.of(2026, 1, 1)));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.assignmentType()).isEqualTo(AssignmentType.SUBSTANTIVE);
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_substantiveWithActivePriorSubstantive_closesPriorIncumbentTheDayBefore() {
        PostIncumbency prior = new PostIncumbency(post, employee, AssignmentType.SUBSTANTIVE, LocalDate.of(2020, 1, 1));
        ReflectionTestUtils.setField(prior, "id", 50L);

        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(POST_ID)).thenReturn(List.of(prior));
        when(postIncumbencyRepository.saveAndFlush(any(PostIncumbency.class))).thenAnswer(inv -> {
            PostIncumbency saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        postIncumbencyService.create(validRequest(AssignmentType.SUBSTANTIVE, LocalDate.of(2026, 3, 15)));

        assertThat(prior.isActive()).isFalse();
        assertThat(prior.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 14));
    }

    @Test
    void create_substantiveOnSameDayAsPriorStart_closesPriorAtItsOwnStartDate_notBeforeIt() {
        PostIncumbency prior = new PostIncumbency(post, employee, AssignmentType.SUBSTANTIVE, LocalDate.of(2026, 3, 15));
        ReflectionTestUtils.setField(prior, "id", 51L);

        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(postIncumbencyRepository.findByPostIdAndActiveTrue(POST_ID)).thenReturn(List.of(prior));
        when(postIncumbencyRepository.saveAndFlush(any(PostIncumbency.class))).thenAnswer(inv -> inv.getArgument(0));

        postIncumbencyService.create(validRequest(AssignmentType.SUBSTANTIVE, LocalDate.of(2026, 3, 15)));

        assertThat(prior.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void create_actingAssignment_doesNotCloseActiveSubstantiveIncumbent() {
        PostIncumbency prior = new PostIncumbency(post, employee, AssignmentType.SUBSTANTIVE, LocalDate.of(2020, 1, 1));
        ReflectionTestUtils.setField(prior, "id", 52L);

        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(postIncumbencyRepository.saveAndFlush(any(PostIncumbency.class))).thenAnswer(inv -> inv.getArgument(0));

        postIncumbencyService.create(validRequest(AssignmentType.ACTING, LocalDate.of(2026, 3, 15)));

        assertThat(prior.isActive()).isTrue();
        assertThat(prior.getEndDate()).isNull();
    }

    @Test
    void create_whenPostMissing_throwsMasterDataNotFoundException() {
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postIncumbencyService.create(validRequest(AssignmentType.SUBSTANTIVE, LocalDate.now())))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postIncumbencyService.create(validRequest(AssignmentType.SUBSTANTIVE, LocalDate.now())))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void create_whenEndDateBeforeStartDate_throwsMasterDataValidationException() {
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        PostIncumbencyRequest invalid = new PostIncumbencyRequest(POST_ID, EMPLOYEE_ID, AssignmentType.ACTING,
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 1), null);

        assertThatThrownBy(() -> postIncumbencyService.create(invalid))
                .isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void end_marksIncumbencyInactiveWithEndDate() {
        PostIncumbency incumbency = new PostIncumbency(post, employee, AssignmentType.ACTING, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(incumbency, "id", 60L);
        when(postIncumbencyRepository.findById(60L)).thenReturn(Optional.of(incumbency));

        PostIncumbencyResponse response = postIncumbencyService.end(60L, LocalDate.of(2026, 6, 1));

        assertThat(response.active()).isFalse();
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void end_whenAlreadyEnded_throwsMasterDataValidationException() {
        PostIncumbency incumbency = new PostIncumbency(post, employee, AssignmentType.ACTING, LocalDate.of(2026, 1, 1));
        incumbency.setActive(false);
        ReflectionTestUtils.setField(incumbency, "id", 61L);
        when(postIncumbencyRepository.findById(61L)).thenReturn(Optional.of(incumbency));

        assertThatThrownBy(() -> postIncumbencyService.end(61L, LocalDate.of(2026, 6, 1)))
                .isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void end_whenEndDateBeforeStartDate_throwsMasterDataValidationException() {
        PostIncumbency incumbency = new PostIncumbency(post, employee, AssignmentType.ACTING, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(incumbency, "id", 62L);
        when(postIncumbencyRepository.findById(62L)).thenReturn(Optional.of(incumbency));

        assertThatThrownBy(() -> postIncumbencyService.end(62L, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(MasterDataValidationException.class);
    }
}
