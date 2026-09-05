package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.PastServiceRecordResponse;
import in.gov.jci.hrms.service.EmployeePastServiceRecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/past-service-records")
public class EmployeePastServiceRecordController {

    private final EmployeePastServiceRecordService pastServiceRecordService;

    public EmployeePastServiceRecordController(EmployeePastServiceRecordService pastServiceRecordService) {
        this.pastServiceRecordService = pastServiceRecordService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<PastServiceRecordResponse> list(@PathVariable Long employeeId) {
        return pastServiceRecordService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PastServiceRecordResponse> create(@PathVariable Long employeeId,
                                                              @Valid @RequestBody PastServiceRecordRequest request) {
        PastServiceRecordResponse created = pastServiceRecordService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/past-service-records/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public PastServiceRecordResponse update(@PathVariable Long employeeId, @PathVariable Long id,
                                             @Valid @RequestBody PastServiceRecordRequest request) {
        return pastServiceRecordService.update(employeeId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long employeeId, @PathVariable Long id) {
        pastServiceRecordService.delete(employeeId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public PastServiceRecordResponse verify(@PathVariable Long employeeId, @PathVariable Long id,
                                             @RequestHeader(value = "X-Acting-User", required = false) String actingUser) {
        return pastServiceRecordService.verify(employeeId, id, actingUser);
    }
}
