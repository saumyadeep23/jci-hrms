package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmploymentCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployeeSpecificationTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Department department;
    private Designation designation;

    private static int panSequence = 0;

    /** Employee's test-fixture convenience constructor hardcodes panNumber "ABCDE1234F" - overridden here so multiple employees in one test don't collide on uq_employees_pan_number_active. */
    private static String uniquePan() {
        return "ABCDE" + String.format("%04d", ++panSequence % 10_000) + "F";
    }

    /** Same collision as uniquePan() above, but for the convenience constructor's hardcoded cpfAcNo "CPF00001" (uq_employees_cpf_ac_no_active). */
    private static String uniqueCpfAcNo() {
        return "CPF" + String.format("%05d", ++panSequence % 100_000);
    }

    private Employee persistEmployee(String code, String firstName, String lastName) {
        if (department == null) {
            String suffix = String.valueOf(System.nanoTime() % 100_000);
            department = entityManager.persistAndFlush(new Department("SD" + suffix, "Spec Department"));
            designation = entityManager.persistAndFlush(new Designation("Spec Designation " + suffix));
        }
        Employee employee = new Employee(code, firstName, lastName, code.toLowerCase() + "@example.com",
                LocalDate.of(1995, 1, 1), department, designation);
        employee.setPanNumber(uniquePan());
        employee.setCpfAcNo(uniqueCpfAcNo());
        return employeeRepository.saveAndFlush(employee);
    }

    @Test
    void search_matchesEmployeeCodeOrFullName_caseInsensitive() {
        persistEmployee("SPEC9001", "Ravi", "Kumar");
        persistEmployee("SPEC9002", "Meera", "Nair");
        entityManager.clear();

        Page<Employee> byCode = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees("spec9001", null, null, null, null, null), PageRequest.of(0, 10));
        assertThat(byCode.getContent()).extracting(Employee::getEmployeeCode).containsExactly("SPEC9001");

        Page<Employee> byName = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees("meera", null, null, null, null, null), PageRequest.of(0, 10));
        assertThat(byName.getContent()).extracting(Employee::getEmployeeCode).containsExactly("SPEC9002");
    }

    @Test
    void statusFilter_defaultsToActive_excludingTerminated() {
        Employee terminated = persistEmployee("SPEC9101", "Old", "Timer");
        terminated.setStatus(EmployeeStatus.TERMINATED);
        terminated.setDeletedAt(null); // terminated but not soft-deleted, to isolate the status filter itself
        employeeRepository.saveAndFlush(terminated);
        persistEmployee("SPEC9102", "Active", "Employee");
        entityManager.clear();

        Page<Employee> defaultStatus = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees("SPEC91", null, null, null, null, null), PageRequest.of(0, 10));
        assertThat(defaultStatus.getContent()).extracting(Employee::getEmployeeCode).containsExactly("SPEC9102");

        Page<Employee> explicitTerminated = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees("SPEC91", null, null, null, null, "TERMINATED"), PageRequest.of(0, 10));
        assertThat(explicitTerminated.getContent()).extracting(Employee::getEmployeeCode).containsExactly("SPEC9101");
    }

    @Test
    void departmentFilter_matchesOnlyThatDepartment() {
        Employee inDept = persistEmployee("SPEC9201", "Dept", "Match");
        Department otherDept = entityManager.persistAndFlush(new Department("SO" + (System.nanoTime() % 100_000), "Other Dept"));
        Employee otherEmployee = new Employee("SPEC9202", "Other", "Employee", "spec9202@example.com",
                LocalDate.of(1995, 1, 1), otherDept, designation);
        otherEmployee.setPanNumber(uniquePan());
        employeeRepository.saveAndFlush(otherEmployee);
        entityManager.clear();

        Page<Employee> result = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees(null, null, null, inDept.getDepartment().getId(), null, null), PageRequest.of(0, 50));

        assertThat(result.getContent()).extracting(Employee::getEmployeeCode).contains("SPEC9201").doesNotContain("SPEC9202");
    }

    @Test
    void employmentTypeFilter_matchesViaEmploymentCategorySubquery() {
        Employee casual = persistEmployee("SPEC9301", "Casual", "Worker");
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(casual, EmploymentCategory.CASUAL);
        category.setDailyWageRate(new BigDecimal("500.00"));
        employmentCategoryRepository.saveAndFlush(category);
        persistEmployee("SPEC9302", "No", "Category");
        entityManager.clear();

        Page<Employee> result = employeeRepository.findAll(
                EmployeeSpecification.filterEmployees("SPEC93", null, null, null, EmploymentCategory.CASUAL, null), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Employee::getEmployeeCode).containsExactly("SPEC9301");
    }
}
