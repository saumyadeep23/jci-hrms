package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EmployeeBankAccountRequest;
import in.gov.jci.hrms.dto.EmployeeBankAccountResponse;
import in.gov.jci.hrms.service.EmployeeBankAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** PIMS_SPEC.md Step 3. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/bank-accounts")
public class EmployeeBankAccountController {

    private final EmployeeBankAccountService bankAccountService;

    public EmployeeBankAccountController(EmployeeBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<EmployeeBankAccountResponse> list(@PathVariable Long employeeId) {
        return bankAccountService.listByEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public ResponseEntity<EmployeeBankAccountResponse> addAccount(@PathVariable Long employeeId,
                                                                    @Valid @RequestBody EmployeeBankAccountRequest request) {
        EmployeeBankAccountResponse created = bankAccountService.addAccount(employeeId, request);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employeeId + "/bank-accounts/" + created.id()))
                .body(created);
    }
}
