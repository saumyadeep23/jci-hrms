package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DesignationRequest;
import in.gov.jci.hrms.dto.DesignationResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.service.DesignationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping({"/api/designations", "/api/v1/admin/masters/designations"})
@PreAuthorize("hasRole('HR_ADMIN')")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @PostMapping
    public ResponseEntity<DesignationResponse> create(@Valid @RequestBody DesignationRequest request) {
        DesignationResponse created = designationService.create(request);
        return ResponseEntity.created(URI.create("/api/designations/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DesignationResponse getById(@PathVariable Long id) {
        return designationService.getById(id);
    }

    @GetMapping
    public Page<DesignationResponse> list(Pageable pageable) {
        return designationService.list(pageable);
    }

    @PutMapping("/{id}")
    public DesignationResponse update(@PathVariable Long id, @Valid @RequestBody DesignationRequest request) {
        return designationService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DesignationResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return designationService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return designationService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        designationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
