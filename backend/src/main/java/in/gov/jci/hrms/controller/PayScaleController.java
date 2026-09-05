package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.dto.PayScaleRequest;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.service.PayScaleService;
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
@RequestMapping({"/api/pay-scales", "/api/v1/admin/masters/pay-scales"})
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class PayScaleController {

    private final PayScaleService payScaleService;

    public PayScaleController(PayScaleService payScaleService) {
        this.payScaleService = payScaleService;
    }

    @PostMapping
    public ResponseEntity<PayScaleResponse> create(@Valid @RequestBody PayScaleRequest request) {
        PayScaleResponse created = payScaleService.create(request);
        return ResponseEntity.created(URI.create("/api/pay-scales/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PayScaleResponse getById(@PathVariable Long id) {
        return payScaleService.getById(id);
    }

    @GetMapping
    public Page<PayScaleResponse> list(Pageable pageable) {
        return payScaleService.list(pageable);
    }

    @PutMapping("/{id}")
    public PayScaleResponse update(@PathVariable Long id, @Valid @RequestBody PayScaleRequest request) {
        return payScaleService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PayScaleResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return payScaleService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return payScaleService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        payScaleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
