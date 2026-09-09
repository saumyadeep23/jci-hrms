package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.exception.DuplicateEmployeeException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final Long DEPARTMENT_ID = 10L;
    private static final Long DESIGNATION_ID = 20L;
    private static final Long RO_ID = 30L;
    private static final Long DPC_ID = 40L;
    private static final Long PAY_SCALE_ID = 50L;

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private RegionalOfficeRepository regionalOfficeRepository;
    @Mock
    private DepartmentalPurchaseCentreRepository dpcRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock
    private EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    @Mock
    private EmployeeCodeGeneratorService employeeCodeGeneratorService;
    @Mock
    private CpfAcNoGeneratorService cpfAcNoGeneratorService;
    @Mock
    private in.gov.jci.hrms.repository.PostMasterRepository postMasterRepository;
    @Mock
    private in.gov.jci.hrms.repository.PostIncumbencyRepository postIncumbencyRepository;
    @Mock
    private PostIncumbencyService postIncumbencyService;

    private EmployeeService employeeService;

    private Department department;
    private Designation designation;
    private RegionalOffice regionalOffice;
    private DepartmentalPurchaseCentre dpc;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(
                employeeRepository, departmentRepository, designationRepository,
                regionalOfficeRepository, dpcRepository, employmentCategoryRepository,
                superannuationDetailsRepository, employeeCodeGeneratorService, cpfAcNoGeneratorService,
                postMasterRepository, postIncumbencyRepository, postIncumbencyService);

        department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", DEPARTMENT_ID);

        designation = new Designation("Backend Developer");
        ReflectionTestUtils.setField(designation, "id", DESIGNATION_ID);

        regionalOffice = new RegionalOffice("RO-DEL", "Delhi RO", "Delhi", CityClass.X, true);
        ReflectionTestUtils.setField(regionalOffice, "id", RO_ID);

        dpc = new DepartmentalPurchaseCentre(regionalOffice, "DPC-DEL-01", "Delhi DPC 1", "New Delhi", "Delhi", true);
        ReflectionTestUtils.setField(dpc, "id", DPC_ID);
    }

    private EmployeeRequest validRequest() {
        return new EmployeeRequest(
                Salutation.MS, "Asha", null, "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null,
                "Indian", null, "ABCDE1234F", "CPF00001", null, "123456789012", "asha.rao@example.com", null, "9876543210", null,
                LocalDate.of(2024, 1, 15), DEPARTMENT_ID, DESIGNATION_ID, RO_ID, DPC_ID, PAY_SCALE_ID,
                EmployeeStatus.ACTIVE, false, null, null, null, null, null
        );
    }

    private void stubMasterLookups() {
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));
        when(regionalOfficeRepository.findById(RO_ID)).thenReturn(Optional.of(regionalOffice));
        when(dpcRepository.findById(DPC_ID)).thenReturn(Optional.of(dpc));
    }

    private Employee employeeFrom(Long id, EmployeeRequest request) {
        Employee employee = new Employee(
                "0001",
                request.salutation(), request.firstName(), request.lastName(), request.gender(),
                request.dateOfBirth(), request.maritalStatus(), request.panNumber(), request.cpfAcNo(), request.personalEmail(),
                request.phone(), request.dateOfJoining(), department, designation
        );
        employee.setRegionalOffice(regionalOffice);
        employee.setDepartmentalPurchaseCentre(dpc);
        employee.setStatus(request.status());
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    @Test
    void create_savesAndReturnsResponse() {
        EmployeeRequest request = validRequest();
        lenient().when(employeeCodeGeneratorService.generateNext()).thenReturn("0001");
        stubMasterLookups();
        Employee saved = employeeFrom(1L, request);
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenReturn(saved);

        EmployeeResponse response = employeeService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.personalEmail()).isEqualTo("asha.rao@example.com");
        assertThat(response.departmentId()).isEqualTo(DEPARTMENT_ID);
        assertThat(response.departmentName()).isEqualTo("Engineering");
        assertThat(response.designationId()).isEqualTo(DESIGNATION_ID);
        assertThat(response.roId()).isEqualTo(RO_ID);
        assertThat(response.dpcId()).isEqualTo(DPC_ID);
        assertThat(response.payScaleId()).isNull();
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void create_withExplicitEmployeeCode_bypassesGenerator() {
        EmployeeRequest request = validRequest();
        stubMasterLookups();
        Employee saved = employeeFrom(7L, request);
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenReturn(saved);

        employeeService.create(request, "0099");

        verify(employeeCodeGeneratorService, never()).generateNext();
    }

    @Test
    void create_withoutOptionalMasterLinks_leavesThemNull() {
        EmployeeRequest request = new EmployeeRequest(
                Salutation.MR, "Ravi", null, "Kumar", Gender.MALE, LocalDate.of(1988, 2, 1), MaritalStatus.MARRIED, null,
                "Indian", null, "FGHIJ5678K", "CPF00003", null, null, "ravi.kumar@example.com", null, "9123456780", null,
                LocalDate.of(2024, 2, 1), DEPARTMENT_ID, DESIGNATION_ID, null, null, null,
                EmployeeStatus.ACTIVE, false, null, null, null, null, null
        );
        when(employeeCodeGeneratorService.generateNext()).thenReturn("0002");
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(designationRepository.findById(DESIGNATION_ID)).thenReturn(Optional.of(designation));
        Employee saved = new Employee("0002", Salutation.MR, "Ravi", "Kumar", Gender.MALE, LocalDate.of(1988, 2, 1),
                MaritalStatus.MARRIED, "FGHIJ5678K", "CPF00003", "ravi.kumar@example.com", "9123456780",
                LocalDate.of(2024, 2, 1), department, designation);
        saved.setStatus(EmployeeStatus.ACTIVE);
        ReflectionTestUtils.setField(saved, "id", 7L);
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenReturn(saved);

        EmployeeResponse response = employeeService.create(request);

        assertThat(response.roId()).isNull();
        assertThat(response.dpcId()).isNull();
        assertThat(response.payScaleId()).isNull();
    }

    @Test
    void create_whenDepartmentMissing_throwsMasterDataNotFoundException() {
        EmployeeRequest request = validRequest();
        when(employeeCodeGeneratorService.generateNext()).thenReturn("0001");
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Department");
    }

    @Test
    void create_whenCodeOrEmailAlreadyInUse_throwsDuplicateEmployeeException() {
        EmployeeRequest request = validRequest();
        when(employeeCodeGeneratorService.generateNext()).thenReturn("0001");
        stubMasterLookups();
        when(employeeRepository.saveAndFlush(any(Employee.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(DuplicateEmployeeException.class);
    }

    @Test
    void getById_whenFound_returnsResponse() {
        Employee employee = employeeFrom(2L, validRequest());
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.getById(2L);

        assertThat(response.id()).isEqualTo(2L);
    }

    @Test
    void getById_whenMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void list_returnsMappedPage() {
        Employee employee = employeeFrom(3L, validRequest());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> page = new PageImpl<>(List.of(employee), pageable, 1);
        when(employeeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<EmployeeResponse> result = employeeService.list(null, null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(3L);
    }

    @Test
    void update_whenFound_updatesFieldsAndReturnsResponse() {
        Employee existing = employeeFrom(4L, validRequest());
        when(employeeRepository.findById(4L)).thenReturn(Optional.of(existing));
        stubMasterLookups();
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeRequest update = new EmployeeRequest(
                Salutation.MS, "Asha", null, "Verma", Gender.FEMALE, LocalDate.of(1990, 5, 1), MaritalStatus.MARRIED, null,
                "Indian", null, "ABCDE1234F", "CPF00002", null, null, "asha.verma@example.com", null, "9876543211", null,
                LocalDate.of(2024, 1, 15), DEPARTMENT_ID, DESIGNATION_ID, RO_ID, DPC_ID, PAY_SCALE_ID,
                EmployeeStatus.ACTIVE, false, null, null, null, null, null
        );

        EmployeeResponse response = employeeService.update(4L, update);

        assertThat(response.lastName()).isEqualTo("Verma");
        assertThat(response.personalEmail()).isEqualTo("asha.verma@example.com");
    }

    @Test
    void update_whenMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.update(100L, validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void update_whenDuplicateEmail_throwsDuplicateEmployeeException() {
        Employee existing = employeeFrom(5L, validRequest());
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(existing));
        stubMasterLookups();
        when(employeeRepository.saveAndFlush(any(Employee.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> employeeService.update(5L, validRequest()))
                .isInstanceOf(DuplicateEmployeeException.class);
    }

    @Test
    void delete_whenFound_softDeletesEmployeeInstead_ofHardDeleting() {
        Employee existing = employeeFrom(6L, validRequest());
        when(employeeRepository.findById(6L)).thenReturn(Optional.of(existing));

        employeeService.delete(6L);

        assertThat(existing.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(existing.getDeletedAt()).isNotNull();
        verify(employeeRepository, never()).deleteById(any());
        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    @Test
    void delete_whenMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.delete(101L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void maskAadhaar_masksAllButLastFourDigits() {
        assertThat(EmployeeService.maskAadhaar("123456789012")).isEqualTo("XXXX-XXXX-9012");
        assertThat(EmployeeService.maskAadhaar(null)).isNull();
    }
}
