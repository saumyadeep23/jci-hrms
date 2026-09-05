package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should exclude soft-deleted employee from findById via @SQLRestriction")
    void shouldExcludeSoftDeletedEmployee() {
        Department department = entityManager.persistAndFlush(new Department("ENG", "Engineering"));
        Designation designation = entityManager.persistAndFlush(new Designation("Backend Developer"));

        Employee employee = new Employee(
                "EMP001",
                "Subhas",
                "Bose",
                "s.bose@jci.gov.in",
                LocalDate.of(1990, 1, 1),
                department,
                designation
        );

        Employee saved = employeeRepository.saveAndFlush(employee);
        assertThat(employeeRepository.findById(saved.getId())).isPresent();

        // Soft-delete
        saved.setStatus(EmployeeStatus.TERMINATED);
        saved.setDeletedAt(Instant.now());
        employeeRepository.saveAndFlush(saved);

        // Clear persistence context to force fresh SELECT from Postgres
        entityManager.clear();

        // Verify @SQLRestriction filters out the record
        Optional<Employee> fetched = employeeRepository.findById(saved.getId());
        assertThat(fetched).isEmpty();
    }

    /**
     * nextCpfAcNoNumber() must (a) increment off the current numeric max
     * rather than some hardcoded number - the real dev dataset already has
     * its own numeric cpf_ac_no values, so this anchors off whatever
     * nextCpfAcNoNumber() already reports as a baseline rather than
     * asserting a fixed absolute figure - and (b) never throw on a legacy
     * non-numeric value like "PF/1001" (CpfAcNoGeneratorService's
     * fallback-suggestion-only nature depends on this).
     */
    @Test
    @DisplayName("nextCpfAcNoNumber() increments off the current numeric max and ignores non-numeric legacy values")
    void nextCpfAcNoNumber_incrementsOffMaxAndIgnoresNonNumericValues() {
        Department department = entityManager.persistAndFlush(new Department("CPF", "CPF Test Department"));
        Designation designation = entityManager.persistAndFlush(new Designation("CPF Test Designation"));

        long baseline = employeeRepository.nextCpfAcNoNumber();

        Employee numeric = new Employee("CPF-NUM", "Numeric", "Fixture", "cpf.numeric@example.com",
                LocalDate.of(1995, 1, 1), department, designation);
        numeric.setPanNumber("CPFNU1234M");
        numeric.setCpfAcNo(String.valueOf(baseline));
        employeeRepository.saveAndFlush(numeric);

        Employee legacy = new Employee("CPF-LEGACY", "Legacy", "Fixture", "cpf.legacy@example.com",
                LocalDate.of(1995, 1, 1), department, designation);
        legacy.setPanNumber("CPFLG5678L");
        legacy.setCpfAcNo("PF/1001");
        employeeRepository.saveAndFlush(legacy);

        assertThat(employeeRepository.nextCpfAcNoNumber()).isEqualTo(baseline + 1);
    }
}