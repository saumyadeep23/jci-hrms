package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.NomineeRequest;
import in.gov.jci.hrms.dto.NomineeResponse;
import in.gov.jci.hrms.service.EmployeeNomineeService;
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
@RequestMapping("/api/employees/{employeeId}/nominees")
public class EmployeeNomineeController {

    private final EmployeeNomineeService nomineeService;

    public EmployeeNomineeController(EmployeeNomineeService nomineeService) {
        this.nomineeService = nomineeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<NomineeResponse> list(@PathVariable Long employeeId) {
        return nomineeService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<NomineeResponse> create(@PathVariable Long employeeId, @Valid @RequestBody NomineeRequest request) {
        NomineeResponse created = nomineeService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/nominees/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public NomineeResponse update(@PathVariable Long employeeId, @PathVariable Long id, @Valid @RequestBody NomineeRequest request) {
        return nomineeService.update(employeeId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long employeeId, @PathVariable Long id) {
        nomineeService.delete(employeeId, id);
        return ResponseEntity.noContent().build();
    }
}
