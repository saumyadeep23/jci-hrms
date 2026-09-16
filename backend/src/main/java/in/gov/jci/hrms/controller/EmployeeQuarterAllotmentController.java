package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.QuarterAllotmentRequest;
import in.gov.jci.hrms.dto.QuarterAllotmentResponse;
import in.gov.jci.hrms.service.EmployeeQuarterAllotmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Employee Company Accommodation (Onboarding/Edit Tab 9) - current and historical quarter allotments for one employee. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/quarter-allotments")
@PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
public class EmployeeQuarterAllotmentController {

    private final EmployeeQuarterAllotmentService quarterAllotmentService;

    public EmployeeQuarterAllotmentController(EmployeeQuarterAllotmentService quarterAllotmentService) {
        this.quarterAllotmentService = quarterAllotmentService;
    }

    @GetMapping
    public List<QuarterAllotmentResponse> list(@PathVariable Long employeeId) {
        return quarterAllotmentService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<QuarterAllotmentResponse> create(@PathVariable Long employeeId,
                                                             @Valid @RequestBody QuarterAllotmentRequest request) {
        QuarterAllotmentResponse created = quarterAllotmentService.create(employeeId, request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employeeId + "/quarter-allotments/" + created.id()))
                .body(created);
    }

    @PutMapping("/{allotmentId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public QuarterAllotmentResponse update(@PathVariable Long employeeId, @PathVariable Long allotmentId,
                                            @Valid @RequestBody QuarterAllotmentRequest request) {
        return quarterAllotmentService.update(employeeId, allotmentId, request);
    }
}
