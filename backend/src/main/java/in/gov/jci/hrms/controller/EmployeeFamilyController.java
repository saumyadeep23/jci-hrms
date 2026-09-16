package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.FamilyDetailsRequest;
import in.gov.jci.hrms.dto.FamilyDetailsResponse;
import in.gov.jci.hrms.service.EmployeeFamilyDetailsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/{employeeId}/family")
public class EmployeeFamilyController {

    private final EmployeeFamilyDetailsService familyDetailsService;

    public EmployeeFamilyController(EmployeeFamilyDetailsService familyDetailsService) {
        this.familyDetailsService = familyDetailsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public ResponseEntity<FamilyDetailsResponse> get(@PathVariable Long employeeId) {
        FamilyDetailsResponse response = familyDetailsService.getByEmployee(employeeId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @PutMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public FamilyDetailsResponse upsert(@PathVariable Long employeeId, @Valid @RequestBody FamilyDetailsRequest request) {
        return familyDetailsService.upsert(employeeId, request);
    }
}
