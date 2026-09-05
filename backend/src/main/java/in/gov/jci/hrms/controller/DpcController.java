package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DecommissionRequest;
import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DpcRequest;
import in.gov.jci.hrms.dto.DpcResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.DpcService;
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
@RequestMapping({"/api/dpcs", "/api/v1/admin/masters/dpcs", "/api/v1/master/dpc"})
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class DpcController {

    private final DpcService dpcService;

    public DpcController(DpcService dpcService) {
        this.dpcService = dpcService;
    }

    @PostMapping
    public ResponseEntity<DpcResponse> create(@Valid @RequestBody DpcRequest request) {
        DpcResponse created = dpcService.create(request);
        return ResponseEntity.created(URI.create("/api/dpcs/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DpcResponse getById(@PathVariable Long id) {
        return dpcService.getById(id);
    }

    @GetMapping
    public Page<DpcResponse> list(Pageable pageable) {
        return dpcService.list(pageable);
    }

    @PutMapping("/{id}")
    public DpcResponse update(@PathVariable Long id, @Valid @RequestBody DpcRequest request) {
        return dpcService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DpcResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return dpcService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return dpcService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @Valid @RequestBody DecommissionRequest request,
                                        Authentication authentication) {
        dpcService.delete(id, request.reason(), SecurityUtils.currentUsername(authentication));
        return ResponseEntity.noContent().build();
    }
}
