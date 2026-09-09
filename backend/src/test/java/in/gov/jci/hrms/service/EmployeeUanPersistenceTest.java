package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB integration test (matching EmployeeFamilyNomineeUpdateTest's own pattern) for the new UAN
 * (EPFO Universal Account Number) field on Employee - PIMS "CPF Trust Ledger + UAN" task, Section 1.
 * The "rejects non-12-digit values" case validates EmployeeRequest's own {@code @Pattern} constraint
 * directly via a Validator (Bean Validation only fires automatically on a real @Valid @RequestBody, not
 * on a manually-constructed record passed straight to the service) - EmployeeControllerTest already
 * covers the equivalent MockMvc-level 400 for other patterned fields (panNumber/aadhaarNumber) the same
 * way this task's own uanNo field now works.
 */
@SpringBootTest
@Transactional
class EmployeeUanPersistenceTest {

    @Autowired private EmployeeService employeeService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private Validator validator;

    private Long departmentId;
    private Long designationId;

    @BeforeEach
    void setUp() {
        departmentId = departmentRepository.save(new Department("UANTEST", "UAN Test Dept")).getId();
        designationId = designationRepository.save(new Designation("UAN Test Officer")).getId();
    }

    private EmployeeRequest requestWithUan(String uanNo, String email) {
        return new EmployeeRequest(
                Salutation.MR, "Uan", null, "Tester", Gender.MALE, LocalDate.of(1990, 1, 1), MaritalStatus.SINGLE, null,
                "Indian", null, "ABCDE1234F", null, uanNo, null, email, null, "9000000000", null,
                LocalDate.of(2024, 1, 1), departmentId, designationId, null, null, null,
                EmployeeStatus.ACTIVE, false, null, null, null, null, null);
    }

    @Test
    void create_withValidUan_persists() {
        EmployeeResponse created = employeeService.create(requestWithUan("101234567890", "uan.create@example.com"));

        assertThat(created.uanNo()).isEqualTo("101234567890");
        assertThat(employeeRepository.findById(created.id()).orElseThrow().getUanNo()).isEqualTo("101234567890");
    }

    @Test
    void create_withoutUan_leavesItNull() {
        EmployeeResponse created = employeeService.create(requestWithUan(null, "uan.blank@example.com"));

        assertThat(created.uanNo()).isNull();
        assertThat(employeeRepository.findById(created.id()).orElseThrow().getUanNo()).isNull();
    }

    @Test
    void update_changesUan_persists() {
        EmployeeResponse created = employeeService.create(requestWithUan("101234567890", "uan.update@example.com"));

        EmployeeResponse updated = employeeService.update(created.id(), requestWithUan("209876543210", "uan.update@example.com"));

        assertThat(updated.uanNo()).isEqualTo("209876543210");
        assertThat(employeeRepository.findById(created.id()).orElseThrow().getUanNo()).isEqualTo("209876543210");
    }

    @Test
    void uanNo_notTwelveDigits_failsBeanValidation() {
        Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(requestWithUan("12345", "uan.invalid@example.com"));

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("uanNo"));
    }

    @Test
    void uanNo_containsLetters_failsBeanValidation() {
        Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(requestWithUan("10123456789A", "uan.invalid2@example.com"));

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("uanNo"));
    }

    @Test
    void uanNo_null_passesBeanValidation() {
        Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(requestWithUan(null, "uan.null@example.com"));

        assertThat(violations).noneSatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("uanNo"));
    }
}
