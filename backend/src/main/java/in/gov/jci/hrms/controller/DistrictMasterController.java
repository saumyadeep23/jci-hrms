package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DistrictMasterRequest;
import in.gov.jci.hrms.dto.DistrictMasterResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.service.DistrictMasterService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

// Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md) - same reasoning
// as StateMasterController: establishment/reference master data, functional owner HR_ADMIN_EST, no
// maker/checker split exists for this data, SYSTEM_ADMIN has no implicit authority.
@RestController
@RequestMapping("/api/v1/admin/masters/districts")
public class DistrictMasterController {

    private final DistrictMasterService districtMasterService;

    public DistrictMasterController(DistrictMasterService districtMasterService) {
        this.districtMasterService = districtMasterService;
    }

    @PostMapping
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public ResponseEntity<DistrictMasterResponse> create(@Valid @RequestBody DistrictMasterRequest request) {
        DistrictMasterResponse created = districtMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/masters/districts/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public DistrictMasterResponse getById(@PathVariable UUID id) {
        return districtMasterService.getById(id);
    }

    @GetMapping
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public Page<DistrictMasterResponse> list(@RequestParam(required = false) UUID stateId, Pageable pageable) {
        return districtMasterService.list(stateId, pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public DistrictMasterResponse update(@PathVariable UUID id, @Valid @RequestBody DistrictMasterRequest request) {
        return districtMasterService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public DistrictMasterResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return districtMasterService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public DependencyCheckResponse dependencies(@PathVariable UUID id) {
        return districtMasterService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        districtMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
