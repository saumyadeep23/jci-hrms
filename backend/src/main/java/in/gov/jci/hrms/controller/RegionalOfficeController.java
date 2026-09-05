package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DecommissionRequest;
import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.dto.RegionalOfficeRequest;
import in.gov.jci.hrms.dto.RegionalOfficeResponse;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.RegionalOfficeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping({"/api/regional-offices", "/api/v1/admin/masters/regional-offices", "/api/v1/master/ro", "/api/v1/offices"})
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class RegionalOfficeController {

    private final RegionalOfficeService regionalOfficeService;

    public RegionalOfficeController(RegionalOfficeService regionalOfficeService) {
        this.regionalOfficeService = regionalOfficeService;
    }

    @PostMapping
    public ResponseEntity<RegionalOfficeResponse> create(@Valid @RequestBody RegionalOfficeRequest request) {
        RegionalOfficeResponse created = regionalOfficeService.create(request);
        return ResponseEntity.created(URI.create("/api/regional-offices/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public RegionalOfficeResponse getById(@PathVariable Long id) {
        return regionalOfficeService.getById(id);
    }

    @GetMapping
    public Page<RegionalOfficeResponse> list(Pageable pageable) {
        return regionalOfficeService.list(pageable);
    }

    @PutMapping("/{id}")
    public RegionalOfficeResponse update(@PathVariable Long id, @Valid @RequestBody RegionalOfficeRequest request) {
        return regionalOfficeService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public RegionalOfficeResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return regionalOfficeService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return regionalOfficeService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @Valid @RequestBody DecommissionRequest request,
                                        Authentication authentication) {
        regionalOfficeService.delete(id, request.reason(), SecurityUtils.currentUsername(authentication));
        return ResponseEntity.noContent().build();
    }
}
