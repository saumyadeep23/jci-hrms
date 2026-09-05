package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeviceRegistrationRequest;
import in.gov.jci.hrms.dto.DeviceStatusUpdateRequest;
import in.gov.jci.hrms.dto.RegisteredDeviceResponse;
import in.gov.jci.hrms.entity.DeviceApprovalStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.RegisteredDevice;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.RegisteredDeviceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * ALMS operational gap #3 - device registration/approval workflow. Purely
 * the registry and its approval lifecycle: nothing here is wired back into
 * MobilePunchService/MobilePunchController, so a punch from an unregistered
 * or still-PENDING_APPROVAL device is not currently blocked - enforcing
 * that at punch time is a separate, deliberately out-of-scope follow-up
 * (the task this shipped under asked for the registration/approval
 * workflow itself, not a punch-time gate).
 */
@Service
@Transactional(readOnly = true)
public class RegisteredDeviceService {

    private static final String ENTITY_NAME = "Registered Device";

    private final RegisteredDeviceRepository registeredDeviceRepository;
    private final EmployeeRepository employeeRepository;

    public RegisteredDeviceService(RegisteredDeviceRepository registeredDeviceRepository, EmployeeRepository employeeRepository) {
        this.registeredDeviceRepository = registeredDeviceRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public RegisteredDeviceResponse register(Long employeeId, DeviceRegistrationRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        // Re-registering the same (employeeId, deviceIdentifier) - e.g. after a REVOKED
        // decision, or just resubmitting - resets it back to PENDING_APPROVAL rather than
        // erroring on the unique index or leaving a stale APPROVED/REVOKED status behind.
        RegisteredDevice device = registeredDeviceRepository
                .findByEmployeeIdAndDeviceIdentifier(employeeId, request.deviceIdentifier())
                .map(existing -> {
                    existing.reRegister(request.deviceName(), request.deviceType(), request.platform());
                    return existing;
                })
                .orElseGet(() -> new RegisteredDevice(employee, request.deviceName(), request.deviceType(),
                        request.deviceIdentifier(), request.platform()));
        return RegisteredDeviceResponse.from(save(device));
    }

    public List<RegisteredDeviceResponse> mine(Long employeeId) {
        return registeredDeviceRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(RegisteredDeviceResponse::from).toList();
    }

    public List<RegisteredDeviceResponse> listAll() {
        return registeredDeviceRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(RegisteredDeviceResponse::from).toList();
    }

    @Transactional
    public RegisteredDeviceResponse updateStatus(Long id, DeviceStatusUpdateRequest request, Long approverEmployeeId) {
        RegisteredDevice device = registeredDeviceRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));

        switch (request.status()) {
            case APPROVED -> {
                Employee approver = employeeRepository.findById(approverEmployeeId)
                        .orElseThrow(() -> new EmployeeNotFoundException(approverEmployeeId));
                device.approve(approver, Instant.now());
            }
            case REVOKED -> device.revoke();
            case PENDING_APPROVAL -> throw new BusinessRuleViolationException(
                    "Cannot set a device back to PENDING_APPROVAL through this endpoint - re-register it instead");
        }
        return RegisteredDeviceResponse.from(save(device));
    }

    private RegisteredDevice save(RegisteredDevice device) {
        try {
            return registeredDeviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException("This device is already registered for this employee");
        }
    }
}
