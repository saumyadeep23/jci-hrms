package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeRequest;
import in.gov.jci.hrms.dto.EmployeeFamilyNomineeCompositeResponse;
import in.gov.jci.hrms.service.EmployeeFamilyNomineeCompositeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backs the redesigned Family & Nominees edit tab's single "Save Changes" action - see EmployeeFamilyNomineeCompositeService's own javadoc. */
@RestController
@RequestMapping("/api/employees/{employeeId}/family-nominees")
public class EmployeeFamilyNomineeCompositeController {

    private final EmployeeFamilyNomineeCompositeService compositeService;

    public EmployeeFamilyNomineeCompositeController(EmployeeFamilyNomineeCompositeService compositeService) {
        this.compositeService = compositeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public EmployeeFamilyNomineeCompositeResponse get(@PathVariable Long employeeId) {
        return compositeService.get(employeeId);
    }

    @PutMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeFamilyNomineeCompositeResponse save(@PathVariable Long employeeId,
                                                        @Valid @RequestBody EmployeeFamilyNomineeCompositeRequest request) {
        return compositeService.save(employeeId, request);
    }
}
