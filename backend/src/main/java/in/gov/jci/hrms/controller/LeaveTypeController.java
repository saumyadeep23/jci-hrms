package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveTypeRequest;
import in.gov.jci.hrms.dto.LeaveTypeResponse;
import in.gov.jci.hrms.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * GET is open to any authenticated employee (LeavePage's leave-type dropdown
 * needs this - an employee applying for leave has to see the available
 * types), mutations are HR_ADMIN/SUPER_ADMIN only.
 */
@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<LeaveTypeResponse> create(@Valid @RequestBody LeaveTypeRequest request) {
        LeaveTypeResponse created = leaveTypeService.create(request);
        return ResponseEntity.created(URI.create("/api/leave-types/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LeaveTypeResponse getById(@PathVariable Long id) {
        return leaveTypeService.getById(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<LeaveTypeResponse> list(Pageable pageable) {
        return leaveTypeService.list(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public LeaveTypeResponse update(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
        return leaveTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        leaveTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
