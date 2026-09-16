package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EmployeeLoanRequest;
import in.gov.jci.hrms.dto.EmployeeLoanResponse;
import in.gov.jci.hrms.dto.LoanForecloseRequest;
import in.gov.jci.hrms.dto.LoanRepaymentRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.service.LoanRepaymentService;
import in.gov.jci.hrms.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/loans")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN', 'SUPER_ADMIN')")
public class LoanController {

    private final LoanService loanService;
    private final LoanRepaymentService loanRepaymentService;

    public LoanController(LoanService loanService, LoanRepaymentService loanRepaymentService) {
        this.loanService = loanService;
        this.loanRepaymentService = loanRepaymentService;
    }

    @PostMapping
    public ResponseEntity<EmployeeLoanResponse> sanction(@Valid @RequestBody EmployeeLoanRequest request) {
        EmployeeLoanResponse created = loanService.sanction(request);
        return ResponseEntity.created(URI.create("/api/loans/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN', 'SUPER_ADMIN') or @loanSec.isSelf(authentication, #id)")
    public EmployeeLoanResponse getById(@PathVariable Long id) {
        return loanService.getById(id);
    }

    @GetMapping
    public Page<EmployeeLoanResponse> list(Pageable pageable) {
        return loanService.list(pageable);
    }

    // Non-financial RBAC migration pass (docs/security/RBAC_MIGRATION_REPORT.md): this general employee
    // loan module was missed by the earlier SEC-003/004 CPF/JCIECCS closure - disburse/foreclose are
    // checker-shaped financial actions, so SUPER_ADMIN is removed here too, narrowed off the class-level
    // grant. No maker != checker enforcement exists in LoanService for this module (unlike
    // CpfLoanApplicationService's requireDifferentFromApplicant) - adding that is a business-workflow
    // change out of this authorization-only task's scope, not attempted here.
    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN')")
    public EmployeeLoanResponse disburse(@PathVariable Long id) {
        return loanService.disburse(id);
    }

    @PostMapping("/{id}/foreclose")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN')")
    public LoanRepaymentResponse foreclose(@PathVariable Long id, @Valid @RequestBody LoanForecloseRequest request) {
        return loanService.foreclose(id, request);
    }

    @PostMapping("/{id}/repayments")
    public ResponseEntity<LoanRepaymentResponse> recordRepayment(@PathVariable Long id,
                                                                   @Valid @RequestBody LoanRepaymentRequest request) {
        LoanRepaymentResponse created = loanRepaymentService.recordRepayment(id, request);
        return ResponseEntity.created(URI.create("/api/loans/repayments/" + created.id())).body(created);
    }

    @GetMapping("/repayments/{id}")
    public LoanRepaymentResponse getRepaymentById(@PathVariable Long id) {
        return loanRepaymentService.getById(id);
    }

    @GetMapping("/repayments")
    public Page<LoanRepaymentResponse> listRepayments(Pageable pageable) {
        return loanRepaymentService.list(pageable);
    }
}
