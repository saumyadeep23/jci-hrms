package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.FunctionalRoleAssignmentRequest;
import in.gov.jci.hrms.dto.FunctionalRoleAssignmentResponse;
import in.gov.jci.hrms.dto.FunctionalRoleMasterResponse;
import in.gov.jci.hrms.service.FunctionalRoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** PIMS/ALMS Functional & Statutory Role Management Subsystem admin console. */
@RestController
@RequestMapping("/api/v1/master/functional-roles")
public class FunctionalRoleController {

    private final FunctionalRoleAssignmentService functionalRoleAssignmentService;

    public FunctionalRoleController(FunctionalRoleAssignmentService functionalRoleAssignmentService) {
        this.functionalRoleAssignmentService = functionalRoleAssignmentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<FunctionalRoleMasterResponse> listRoles() {
        return functionalRoleAssignmentService.listRoles();
    }

    @GetMapping("/assignments")
    @PreAuthorize("isAuthenticated()")
    public List<FunctionalRoleAssignmentResponse> listAssignments(@RequestParam(required = false) String roleCode,
                                                                    @RequestParam(required = false) Long departmentId,
                                                                    @RequestParam(required = false) Long officeId,
                                                                    @RequestParam(required = false) Long employeeId,
                                                                    @RequestParam(defaultValue = "true") boolean activeOnly) {
        return functionalRoleAssignmentService.listAssignments(roleCode, departmentId, officeId, employeeId, activeOnly);
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<FunctionalRoleAssignmentResponse> assign(@Valid @RequestBody FunctionalRoleAssignmentRequest request) {
        FunctionalRoleAssignmentResponse created = functionalRoleAssignmentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/master/functional-roles/assignments/" + created.id())).body(created);
    }

    @PatchMapping("/assignments/{id}/relieve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public FunctionalRoleAssignmentResponse relieve(@PathVariable UUID id,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) {
        return functionalRoleAssignmentService.relieve(id, validTo != null ? validTo : LocalDate.now());
    }
}
