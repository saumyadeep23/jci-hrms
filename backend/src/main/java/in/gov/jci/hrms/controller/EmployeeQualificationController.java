package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.dto.QualificationResponse;
import in.gov.jci.hrms.service.EmployeeQualificationService;
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
@RequestMapping("/api/employees/{employeeId}/qualifications")
public class EmployeeQualificationController {

    private final EmployeeQualificationService qualificationService;

    public EmployeeQualificationController(EmployeeQualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<QualificationResponse> list(@PathVariable Long employeeId) {
        return qualificationService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<QualificationResponse> create(@PathVariable Long employeeId,
                                                          @Valid @RequestBody QualificationRequest request) {
        QualificationResponse created = qualificationService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/qualifications/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public QualificationResponse update(@PathVariable Long employeeId, @PathVariable Long id,
                                         @Valid @RequestBody QualificationRequest request) {
        return qualificationService.update(employeeId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long employeeId, @PathVariable Long id) {
        qualificationService.delete(employeeId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public QualificationResponse verify(@PathVariable Long employeeId, @PathVariable Long id,
                                         @RequestHeader(value = "X-Acting-User", required = false) String actingUser) {
        return qualificationService.verify(employeeId, id, actingUser);
    }
}
