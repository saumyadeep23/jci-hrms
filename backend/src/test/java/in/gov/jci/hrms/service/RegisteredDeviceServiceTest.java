package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeviceRegistrationRequest;
import in.gov.jci.hrms.dto.DeviceStatusUpdateRequest;
import in.gov.jci.hrms.dto.RegisteredDeviceResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DeviceApprovalStatus;
import in.gov.jci.hrms.entity.DeviceType;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.RegisteredDevice;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegisteredDeviceRepository;
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
class RegisteredDeviceServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long APPROVER_ID = 2L;

    @Mock
    private RegisteredDeviceRepository registeredDeviceRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private RegisteredDeviceService service;
    private Employee employee;
    private Employee approver;

    @BeforeEach
    void setUp() {
        service = new RegisteredDeviceService(registeredDeviceRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        ReflectionTestUtils.setField(employee, "fullName", "Asha Rao"); // DB-generated column, insertable=false - not set by the constructor

        approver = new Employee("EMP-002", "Rohit", "Sharma", "rohit.sharma@example.com",
                LocalDate.of(2023, 1, 15), department, designation);
        ReflectionTestUtils.setField(approver, "id", APPROVER_ID);
        ReflectionTestUtils.setField(approver, "fullName", "Rohit Sharma");
    }

    private DeviceRegistrationRequest validRequest() {
        return new DeviceRegistrationRequest("Asha's Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android 14");
    }

    @Test
    void register_whenNewDevice_savesAsPendingApproval() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(registeredDeviceRepository.findByEmployeeIdAndDeviceIdentifier(EMPLOYEE_ID, "device-uuid-123")).thenReturn(Optional.empty());
        when(registeredDeviceRepository.saveAndFlush(any(RegisteredDevice.class))).thenAnswer(inv -> {
            RegisteredDevice saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        RegisteredDeviceResponse response = service.register(EMPLOYEE_ID, validRequest());

        assertThat(response.status()).isEqualTo(DeviceApprovalStatus.PENDING_APPROVAL);
        assertThat(response.deviceIdentifier()).isEqualTo("device-uuid-123");
        assertThat(response.employeeCode()).isEqualTo("EMP-001");
    }

    @Test
    void register_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(EMPLOYEE_ID, validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void register_whenReRegisteringARevokedDevice_resetsToPendingApproval() {
        RegisteredDevice existing = new RegisteredDevice(employee, "Old Name", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android 13");
        existing.revoke();
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(registeredDeviceRepository.findByEmployeeIdAndDeviceIdentifier(EMPLOYEE_ID, "device-uuid-123")).thenReturn(Optional.of(existing));
        when(registeredDeviceRepository.saveAndFlush(any(RegisteredDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisteredDeviceResponse response = service.register(EMPLOYEE_ID, validRequest());

        assertThat(response.status()).isEqualTo(DeviceApprovalStatus.PENDING_APPROVAL);
        assertThat(response.deviceName()).isEqualTo("Asha's Phone");
    }

    @Test
    void mine_returnsOnlyThatEmployeesDevices() {
        RegisteredDevice device = new RegisteredDevice(employee, "Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android");
        when(registeredDeviceRepository.findByEmployeeIdOrderByCreatedAtDesc(EMPLOYEE_ID)).thenReturn(List.of(device));

        List<RegisteredDeviceResponse> result = service.mine(EMPLOYEE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeeCode()).isEqualTo("EMP-001");
    }

    @Test
    void updateStatus_toApproved_setsApproverAndTimestamp() {
        RegisteredDevice device = new RegisteredDevice(employee, "Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android");
        when(registeredDeviceRepository.findById(10L)).thenReturn(Optional.of(device));
        when(employeeRepository.findById(APPROVER_ID)).thenReturn(Optional.of(approver));
        when(registeredDeviceRepository.saveAndFlush(any(RegisteredDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisteredDeviceResponse response = service.updateStatus(10L, new DeviceStatusUpdateRequest(DeviceApprovalStatus.APPROVED), APPROVER_ID);

        assertThat(response.status()).isEqualTo(DeviceApprovalStatus.APPROVED);
        assertThat(response.approvedByName()).isEqualTo("Rohit Sharma");
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    void updateStatus_toRevoked_clearsApprovalButDoesNotNeedApprover() {
        RegisteredDevice device = new RegisteredDevice(employee, "Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android");
        device.approve(approver, java.time.Instant.now());
        when(registeredDeviceRepository.findById(10L)).thenReturn(Optional.of(device));
        when(registeredDeviceRepository.saveAndFlush(any(RegisteredDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisteredDeviceResponse response = service.updateStatus(10L, new DeviceStatusUpdateRequest(DeviceApprovalStatus.REVOKED), APPROVER_ID);

        assertThat(response.status()).isEqualTo(DeviceApprovalStatus.REVOKED);
    }

    @Test
    void updateStatus_toPendingApproval_throwsBusinessRuleViolationException() {
        RegisteredDevice device = new RegisteredDevice(employee, "Phone", DeviceType.ANDROID_MOBILE, "device-uuid-123", "Android");
        when(registeredDeviceRepository.findById(10L)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.updateStatus(10L, new DeviceStatusUpdateRequest(DeviceApprovalStatus.PENDING_APPROVAL), APPROVER_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateStatus_whenDeviceMissing_throwsMasterDataNotFoundException() {
        when(registeredDeviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, new DeviceStatusUpdateRequest(DeviceApprovalStatus.APPROVED), APPROVER_ID))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
