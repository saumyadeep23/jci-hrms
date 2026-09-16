package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.dto.StateMasterRequest;
import in.gov.jci.hrms.dto.StateMasterResponse;
import in.gov.jci.hrms.service.StateMasterService;
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
import java.util.UUID;

// Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md), confirmed
// ownership: State/District are establishment/reference master data, not technical SYSTEM_ADMIN
// configuration - functional owner is HR_ADMIN_EST. No maker/checker split exists for this master
// data (StateMasterService's create/update/updateStatus/delete are all immediate, single-step
// mutations - no pending/draft state), so per the confirmed direction HR_ADMIN_EST remains the sole
// authoritative maintainer; HR_MAKER_EST is not granted anything here (would invent a maker step
// this data model doesn't support). SYSTEM_ADMIN has no implicit authority - it must be explicitly
// granted ESTABLISHMENT_MAINTAIN like any other user if that's ever intended.
@RestController
@RequestMapping("/api/v1/admin/masters/states")
public class StateMasterController {

    private final StateMasterService stateMasterService;

    public StateMasterController(StateMasterService stateMasterService) {
        this.stateMasterService = stateMasterService;
    }

    @PostMapping
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public ResponseEntity<StateMasterResponse> create(@Valid @RequestBody StateMasterRequest request) {
        StateMasterResponse created = stateMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/masters/states/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public StateMasterResponse getById(@PathVariable UUID id) {
        return stateMasterService.getById(id);
    }

    @GetMapping
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public Page<StateMasterResponse> list(Pageable pageable) {
        return stateMasterService.list(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public StateMasterResponse update(@PathVariable UUID id, @Valid @RequestBody StateMasterRequest request) {
        return stateMasterService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public StateMasterResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return stateMasterService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_VIEW')")
    public DependencyCheckResponse dependencies(@PathVariable UUID id) {
        return stateMasterService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'ESTABLISHMENT_MAINTAIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        stateMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
