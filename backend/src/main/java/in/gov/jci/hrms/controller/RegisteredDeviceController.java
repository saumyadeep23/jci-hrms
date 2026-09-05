package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DeviceRegistrationRequest;
import in.gov.jci.hrms.dto.DeviceStatusUpdateRequest;
import in.gov.jci.hrms.dto.RegisteredDeviceResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.RegisteredDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ALMS operational gap #3 - device registration workflow (ESS &amp; Admin). */
@RestController
@RequestMapping("/api/v1/attendance/devices")
public class RegisteredDeviceController {

    private final RegisteredDeviceService registeredDeviceService;

    public RegisteredDeviceController(RegisteredDeviceService registeredDeviceService) {
        this.registeredDeviceService = registeredDeviceService;
    }

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RegisteredDeviceResponse> register(@Valid @RequestBody DeviceRegistrationRequest request,
                                                                Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredDeviceService.register(currentEmployeeId(authentication), request));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<RegisteredDeviceResponse> mine(Authentication authentication) {
        return registeredDeviceService.mine(currentEmployeeId(authentication));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public List<RegisteredDeviceResponse> listAll() {
        return registeredDeviceService.listAll();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public RegisteredDeviceResponse updateStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusUpdateRequest request,
                                                   Authentication authentication) {
        return registeredDeviceService.updateStatus(id, request, currentEmployeeId(authentication));
    }

    private Long currentEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve your employee record");
        }
        return employeeId;
    }
}
