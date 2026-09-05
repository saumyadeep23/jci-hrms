package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.dto.QualificationResponse;
import in.gov.jci.hrms.entity.CourseType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeQualification;
import in.gov.jci.hrms.entity.QualificationLevel;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeQualificationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeQualificationServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private EmployeeQualificationRepository qualificationRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeeQualificationService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new EmployeeQualificationService(qualificationRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Backend Developer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private QualificationRequest validRequest() {
        return new QualificationRequest(QualificationLevel.GRADUATION, "B.Tech", "Computer Science",
                "Delhi University", "IIT Delhi", 2015, new BigDecimal("82.50"), null, CourseType.FULL_TIME,
                true, null);
    }

    @Test
    void create_whenEmployeeFound_savesAndReturnsResponse() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(qualificationRepository.saveAndFlush(any(EmployeeQualification.class)))
                .thenAnswer(invocation -> {
                    EmployeeQualification q = invocation.getArgument(0);
                    ReflectionTestUtils.setField(q, "id", 5L);
                    return q;
                });

        QualificationResponse response = service.create(EMPLOYEE_ID, validRequest());

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(response.degreeTitle()).isEqualTo("B.Tech");
        assertThat(response.highestQualification()).isTrue();
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void listByEmployee_returnsMappedResponses() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        EmployeeQualification qualification = new EmployeeQualification(
                employee, QualificationLevel.POST_GRADUATION, "M.Tech", "IIT Bombay", 2018);
        ReflectionTestUtils.setField(qualification, "id", 6L);
        when(qualificationRepository.findByEmployeeIdOrderByPassingYearDesc(EMPLOYEE_ID))
                .thenReturn(List.of(qualification));

        List<QualificationResponse> responses = service.listByEmployee(EMPLOYEE_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).degreeTitle()).isEqualTo("M.Tech");
    }

    @Test
    void update_whenQualificationBelongsToDifferentEmployee_throwsMasterDataNotFoundException() {
        Department department = new Department("HR", "Human Resources");
        Designation designation = new Designation("HR Executive");
        Employee otherEmployee = new Employee("EMP-002", "Ravi", "Kumar", "ravi.kumar@example.com",
                LocalDate.of(2024, 1, 1), department, designation);
        ReflectionTestUtils.setField(otherEmployee, "id", 2L);

        EmployeeQualification qualification = new EmployeeQualification(
                otherEmployee, QualificationLevel.GRADUATION, "B.Com", "Delhi University", 2010);
        ReflectionTestUtils.setField(qualification, "id", 8L);
        when(qualificationRepository.findById(8L)).thenReturn(Optional.of(qualification));

        assertThatThrownBy(() -> service.update(EMPLOYEE_ID, 8L, validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void verify_whenFound_marksVerified() {
        EmployeeQualification qualification = new EmployeeQualification(
                employee, QualificationLevel.GRADUATION, "B.Tech", "Delhi University", 2015);
        ReflectionTestUtils.setField(qualification, "id", 7L);
        when(qualificationRepository.findById(7L)).thenReturn(Optional.of(qualification));
        when(qualificationRepository.saveAndFlush(any(EmployeeQualification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QualificationResponse response = service.verify(EMPLOYEE_ID, 7L, "hr-admin");

        assertThat(response.verified()).isTrue();
        assertThat(response.verifiedBy()).isEqualTo("hr-admin");
        assertThat(response.verifiedAt()).isNotNull();
    }

    @Test
    void delete_whenFound_softDeletesRatherThanHardDeleting() {
        EmployeeQualification qualification = new EmployeeQualification(
                employee, QualificationLevel.GRADUATION, "B.Tech", "Delhi University", 2015);
        ReflectionTestUtils.setField(qualification, "id", 9L);
        when(qualificationRepository.findById(9L)).thenReturn(Optional.of(qualification));

        service.delete(EMPLOYEE_ID, 9L);

        assertThat(qualification.getDeletedAt()).isNotNull();
    }
}
