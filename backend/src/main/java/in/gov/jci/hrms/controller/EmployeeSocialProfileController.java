package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.SocialProfileRequest;
import in.gov.jci.hrms.dto.SocialProfileResponse;
import in.gov.jci.hrms.service.EmployeeSocialProfileService;
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
@RequestMapping("/api/employees/{employeeId}/social-profile")
public class EmployeeSocialProfileController {

    private final EmployeeSocialProfileService socialProfileService;

    public EmployeeSocialProfileController(EmployeeSocialProfileService socialProfileService) {
        this.socialProfileService = socialProfileService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public ResponseEntity<SocialProfileResponse> get(@PathVariable Long employeeId) {
        SocialProfileResponse response = socialProfileService.getByEmployee(employeeId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public SocialProfileResponse upsert(@PathVariable Long employeeId, @Valid @RequestBody SocialProfileRequest request) {
        return socialProfileService.upsert(employeeId, request);
    }
}
