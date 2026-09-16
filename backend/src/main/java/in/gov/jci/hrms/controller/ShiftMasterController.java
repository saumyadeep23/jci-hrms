package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.MasterStatusUpdateRequest;
import in.gov.jci.hrms.dto.ShiftMasterRequest;
import in.gov.jci.hrms.dto.ShiftMasterResponse;
import in.gov.jci.hrms.service.ShiftMasterService;
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

/** Master Data Console's Shift Master (Watchmen/Security/General rosters). */
@RestController
@RequestMapping("/api/v1/attendance/shifts")
@PreAuthorize("hasRole('HR_ADMIN')")
public class ShiftMasterController {

    private final ShiftMasterService shiftMasterService;

    public ShiftMasterController(ShiftMasterService shiftMasterService) {
        this.shiftMasterService = shiftMasterService;
    }

    @PostMapping
    public ResponseEntity<ShiftMasterResponse> create(@Valid @RequestBody ShiftMasterRequest request) {
        ShiftMasterResponse created = shiftMasterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/attendance/shifts/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ShiftMasterResponse getById(@PathVariable Long id) {
        return shiftMasterService.getById(id);
    }

    @GetMapping
    public Page<ShiftMasterResponse> list(Pageable pageable) {
        return shiftMasterService.list(pageable);
    }

    @PutMapping("/{id}")
    public ShiftMasterResponse update(@PathVariable Long id, @Valid @RequestBody ShiftMasterRequest request) {
        return shiftMasterService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ShiftMasterResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MasterStatusUpdateRequest request) {
        return shiftMasterService.updateStatus(id, request.active());
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return shiftMasterService.dependencies(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shiftMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
