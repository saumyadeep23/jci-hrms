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

@RestController
@RequestMapping("/api/v1/admin/masters/states")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StateMasterController {

    private final StateMasterService stateMasterService;

    public StateMasterController(StateMasterService stateMasterService) {
        this.stateMasterService = stateMasterService;
    }

    @PostMapping
    public ResponseEntity<StateMasterResponse> create(@Valid @RequestBody StateMasterRequest request) {
        StateMasterResponse created = stateMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/masters/states/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public StateMasterResponse getById(@PathVariable UUID id) {
        return stateMasterService.getById(id);
    }

    @GetMapping
    public Page<StateMasterResponse> list(Pageable pageable) {
        return stateMasterService.list(pageable);
    }

    @PutMapping("/{id}")
    public StateMasterResponse update(@PathVariable UUID id, @Valid @RequestBody StateMasterRequest request) {
        return stateMasterService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public StateMasterResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return stateMasterService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable UUID id) {
        return stateMasterService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        stateMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
