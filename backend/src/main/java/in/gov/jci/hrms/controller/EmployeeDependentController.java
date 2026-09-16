package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependentRequest;
import in.gov.jci.hrms.dto.DependentResponse;
import in.gov.jci.hrms.service.EmployeeDependentService;
import jakarta.validation.Valid;
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
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/dependents")
public class EmployeeDependentController {

    private final EmployeeDependentService dependentService;

    public EmployeeDependentController(EmployeeDependentService dependentService) {
        this.dependentService = dependentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<DependentResponse> list(@PathVariable Long employeeId) {
        return dependentService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<DependentResponse> create(@PathVariable Long employeeId, @Valid @RequestBody DependentRequest request) {
        DependentResponse created = dependentService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/dependents/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public DependentResponse update(@PathVariable Long employeeId, @PathVariable Long id, @Valid @RequestBody DependentRequest request) {
        return dependentService.update(employeeId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long employeeId, @PathVariable Long id) {
        dependentService.delete(employeeId, id);
        return ResponseEntity.noContent().build();
    }
}
