package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.ContractualEngagementResponse;
import in.gov.jci.hrms.dto.OutsourcedDeploymentResponse;
import in.gov.jci.hrms.dto.RenewContractRequest;
import in.gov.jci.hrms.dto.RenewDeploymentRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.ContractualEngagement;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.OutsourcedDeployment;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.entity.VendorMaster;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.ContractualEngagementRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.OutsourcedDeploymentRepository;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractRenewalServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ContractualEngagementRepository contractualEngagementRepository;
    @Mock private OutsourcedDeploymentRepository outsourcedDeploymentRepository;
    @Mock private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Mock private VendorMasterRepository vendorMasterRepository;

    private ContractRenewalService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new ContractRenewalService(employeeRepository, contractualEngagementRepository,
                outsourcedDeploymentRepository, gradeScaleMasterRepository, vendorMasterRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Engineer");
        employee = new Employee("EMP0001", Salutation.MR, "Ravi", "Kumar", Gender.MALE, LocalDate.of(1990, 1, 1),
                MaritalStatus.SINGLE, "ABCDE1234F", "CPF00001", "ravi@example.com", "9876543210", LocalDate.of(2015, 1, 1),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);
    }

    @Test
    void renewContract_deactivatesPreviousAndSavesNewCurrentRow() {
        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        ContractualEngagement previous = new ContractualEngagement(
                employee, new BigDecimal("30000.00"), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "APR-OLD");
        when(contractualEngagementRepository.findByEmployeeIdAndCurrentTrue(100L)).thenReturn(Optional.of(previous));
        when(contractualEngagementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RenewContractRequest request = new RenewContractRequest(new BigDecimal("35000.00"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), "APR-NEW", null, "Renewed one year");

        ContractualEngagementResponse response = service.renewContract(100L, request);

        assertThat(previous.isCurrent()).isFalse();
        assertThat(response.current()).isTrue();
        assertThat(response.monthlyLumpsum()).isEqualByComparingTo("35000.00");
        assertThat(response.approvalRefNo()).isEqualTo("APR-NEW");
    }

    @Test
    void renewDeployment_deactivatesPreviousAndSavesNewCurrentRow() {
        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        OutsourcedDeployment previous = new OutsourcedDeployment(
                employee, new BigDecimal("25000.00"), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "WO-OLD");
        when(outsourcedDeploymentRepository.findByEmployeeIdAndCurrentTrue(100L)).thenReturn(Optional.of(previous));
        when(outsourcedDeploymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        VendorMaster vendor = new VendorMaster("VEN-001", "Acme Manpower", LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1));
        ReflectionTestUtils.setField(vendor, "id", 5L);
        when(vendorMasterRepository.findById(5L)).thenReturn(Optional.of(vendor));

        RenewDeploymentRequest request = new RenewDeploymentRequest(5L, new BigDecimal("27000.00"), new BigDecimal("30000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "WO-NEW", null);

        OutsourcedDeploymentResponse response = service.renewDeployment(100L, request);

        assertThat(previous.isCurrent()).isFalse();
        assertThat(response.current()).isTrue();
        assertThat(response.monthlyCtc()).isEqualByComparingTo("27000.00");
        assertThat(response.vendorId()).isEqualTo(5L);
    }

    @Test
    void renewContract_unknownEmployee_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        RenewContractRequest request = new RenewContractRequest(new BigDecimal("35000.00"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), "APR-NEW", null, null);

        assertThatThrownBy(() -> service.renewContract(999L, request)).isInstanceOf(EmployeeNotFoundException.class);
    }
}
