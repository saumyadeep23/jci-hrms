package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.dto.VendorMasterRequest;
import in.gov.jci.hrms.dto.VendorMasterResponse;
import in.gov.jci.hrms.service.VendorMasterService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** PIMS_SPEC.md Section 1.A.7 (Manpower Vendors) and Section 6 admin-console route table. */
@RestController
@RequestMapping({"/api/vendors", "/api/v1/admin/masters/vendors"})
@PreAuthorize("hasRole('HR_ADMIN')")
public class VendorMasterController {

    private final VendorMasterService vendorMasterService;

    public VendorMasterController(VendorMasterService vendorMasterService) {
        this.vendorMasterService = vendorMasterService;
    }

    @PostMapping
    public ResponseEntity<VendorMasterResponse> create(@Valid @RequestBody VendorMasterRequest request) {
        VendorMasterResponse created = vendorMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/masters/vendors/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public VendorMasterResponse getById(@PathVariable Long id) {
        return vendorMasterService.getById(id);
    }

    @GetMapping
    public Page<VendorMasterResponse> list(Pageable pageable) {
        return vendorMasterService.list(pageable);
    }

    @PutMapping("/{id}")
    public VendorMasterResponse update(@PathVariable Long id, @Valid @RequestBody VendorMasterRequest request) {
        return vendorMasterService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public VendorMasterResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return vendorMasterService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return vendorMasterService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vendorMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
