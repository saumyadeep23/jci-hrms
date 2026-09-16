package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.EmployeeAddressResponse;
import in.gov.jci.hrms.service.EmployeeAddressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PIMS_SPEC.md Step 2. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/addresses")
public class EmployeeAddressController {

    private final EmployeeAddressService addressService;

    public EmployeeAddressController(EmployeeAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<EmployeeAddressResponse> list(@PathVariable Long employeeId) {
        return addressService.listByEmployee(employeeId);
    }

    @PutMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public EmployeeAddressResponse upsert(@PathVariable Long employeeId, @Valid @RequestBody EmployeeAddressRequest request) {
        return addressService.upsert(employeeId, request);
    }
}
