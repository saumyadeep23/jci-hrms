package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfEpsEligibilityResponse;
import in.gov.jci.hrms.dto.CpfEpsEligibilityStatus;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CpfEpsEligibilityService.checkEligibility() - the "Check EPS Pension Eligibility" panel's backing logic.
 * Deliberately tests the placeholder rule (configurable minimum pensionable service, default 10 years) as
 * documented, not as confirmed JCI policy - see the service's own javadoc.
 */
@SpringBootTest
@Transactional
class CpfEpsEligibilityServiceTest {

    @Autowired private CpfEpsEligibilityService epsEligibilityService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private ExitClearanceRequestRepository exitClearanceRequestRepository;

    private Department department;
    private Designation designation;
    private int panSequence = 0;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFEPS", "CPF EPS Test Dept"));
        designation = designationRepository.save(new Designation("CPF EPS Test Officer"));
    }

    private Employee newEmployee(String code, String email, LocalDate dateOfJoining, EmployeeStatus status, boolean epsEligible) {
        Employee employee = new Employee(code, "Eps", "Tester", email, dateOfJoining, department, designation);
        employee.setStatus(status);
        employee.setEpsEligible(epsEligible);
        employee.setPanNumber("CPFEP" + (panSequence++) + "234F");
        return employeeRepository.save(employee);
    }

    @Test
    void checkEligibility_notEnrolledInEps_isNotEligible() {
        Employee employee = newEmployee("EMP-EPS-1", "eps1@example.com", LocalDate.of(2000, 1, 1), EmployeeStatus.ACTIVE, false);

        CpfEpsEligibilityResponse response = epsEligibilityService.checkEligibility(employee.getId());

        assertThat(response.epsMember()).isFalse();
        assertThat(response.eligibility()).isEqualTo(CpfEpsEligibilityStatus.NOT_ELIGIBLE);
    }

    @Test
    void checkEligibility_activeMemberOverTenYearsService_isEligible() {
        Employee employee = newEmployee("EMP-EPS-2", "eps2@example.com", LocalDate.of(2010, 1, 1), EmployeeStatus.ACTIVE, true);

        CpfEpsEligibilityResponse response = epsEligibilityService.checkEligibility(employee.getId());

        assertThat(response.eligibility()).isEqualTo(CpfEpsEligibilityStatus.ELIGIBLE);
        assertThat(response.dateOfSeparation()).isNull();
    }

    @Test
    void checkEligibility_activeMemberUnderTenYearsService_isReviewRequired() {
        Employee employee = newEmployee("EMP-EPS-3", "eps3@example.com", LocalDate.now().minusYears(3), EmployeeStatus.ACTIVE, true);

        CpfEpsEligibilityResponse response = epsEligibilityService.checkEligibility(employee.getId());

        assertThat(response.eligibility()).isEqualTo(CpfEpsEligibilityStatus.REVIEW_REQUIRED);
    }

    @Test
    void checkEligibility_separatedMemberUnderTenYearsService_isNotEligible() {
        Employee employee = newEmployee("EMP-EPS-4", "eps4@example.com", LocalDate.of(2020, 1, 1), EmployeeStatus.RESIGNED, true);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.RESIGNATION, LocalDate.of(2023, 1, 1), "resignation");
        clearance.setReleaseOrderDate(LocalDate.of(2023, 1, 1));
        exitClearanceRequestRepository.save(clearance);

        CpfEpsEligibilityResponse response = epsEligibilityService.checkEligibility(employee.getId());

        assertThat(response.dateOfSeparation()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(response.eligibility()).isEqualTo(CpfEpsEligibilityStatus.NOT_ELIGIBLE);
        assertThat(response.eligibilityBasis()).contains("withdrawal benefit");
    }

    @Test
    void checkEligibility_separatedMemberOverTenYearsService_isEligible() {
        Employee employee = newEmployee("EMP-EPS-5", "eps5@example.com", LocalDate.of(2005, 1, 1), EmployeeStatus.RETIRED, true);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 1, 1), "retirement");
        clearance.setReleaseOrderDate(LocalDate.of(2026, 1, 1));
        exitClearanceRequestRepository.save(clearance);

        CpfEpsEligibilityResponse response = epsEligibilityService.checkEligibility(employee.getId());

        assertThat(response.eligibility()).isEqualTo(CpfEpsEligibilityStatus.ELIGIBLE);
    }
}
