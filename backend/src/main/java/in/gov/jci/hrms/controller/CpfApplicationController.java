package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfApplicationEligibilityResponse;
import in.gov.jci.hrms.dto.CpfApplicationRequest;
import in.gov.jci.hrms.dto.CpfApplicationResponse;
import in.gov.jci.hrms.dto.CpfApplicationSanctionRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The rule-driven CPF withdrawal application lifecycle (CpfApplicationService) - apply -> sanction -> disburse. Separate from CpfLoanController/cpf_loan_applications - see CpfApplication's own javadoc. */
@RestController
@RequestMapping("/api/v1/payroll/trust/withdrawal-applications")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfApplicationController {

    private final CpfApplicationService applicationService;

    public CpfApplicationController(CpfApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** Task 4 Part 20: widened to EMPLOYEE for self-service (Simulator + Apply for Loan), restricted to
     * the caller's own employeeCode via @cpfApplicationSec.isSelf - admins retain unrestricted access. */
    @GetMapping("/eligibility")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN') or (hasRole('EMPLOYEE') and @cpfApplicationSec.isSelf(authentication, #employeeCode))")
    public CpfApplicationEligibilityResponse eligibility(@RequestParam String employeeCode, @RequestParam String purposeCode,
                                                          @RequestParam(required = false) java.math.BigDecimal basicPlusDa,
                                                          @RequestParam(required = false) java.math.BigDecimal propertyCost,
                                                          @RequestParam(required = false) java.math.BigDecimal payrollDeductionCapacity,
                                                          @RequestParam(required = false) java.math.BigDecimal outstandingLoan,
                                                          @RequestParam(required = false) java.math.BigDecimal requestedAmount,
                                                          @RequestParam(required = false) Integer tenureMonths,
                                                          Authentication authentication) {
        return applicationService.checkEligibility(employeeCode, purposeCode, basicPlusDa, propertyCost, payrollDeductionCapacity,
                outstandingLoan, requestedAmount, tenureMonths);
    }

    @GetMapping("/employee/{employeeCode}")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN') or (hasRole('EMPLOYEE') and @cpfApplicationSec.isSelf(authentication, #employeeCode))")
    public List<CpfApplicationResponse> byEmployee(@PathVariable String employeeCode) {
        return applicationService.findByEmployee(employeeCode);
    }

    /** Task 4 Part 20: widened to EMPLOYEE for self-service - an employee may only apply for themselves
     * (@cpfApplicationSec.isSelf against the request body's own employeeCode, not a path/query param).
     * Sanction/disburse/reject remain CPF_ADMIN-only, untouched - "may not approve own
     * application" stays enforced by simply never granting EMPLOYEE those three endpoints. */
    @PostMapping
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN') or (hasRole('EMPLOYEE') and @cpfApplicationSec.isSelf(authentication, #request.employeeCode()))")
    public ResponseEntity<CpfApplicationResponse> apply(@Valid @RequestBody CpfApplicationRequest request, Authentication authentication) {
        String createdBy = SecurityUtils.currentUsername(authentication);
        CpfApplicationResponse created = applicationService.apply(request, createdBy != null ? createdBy : "system");
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payroll/trust/withdrawal-applications/" + created.id()))
                .body(created);
    }

    // SEC-010 (docs/security/RBAC_MIGRATION_REPORT.md): sanction/disburse/reject are financial
    // checker-only approval actions - SUPER_ADMIN (the legacy JWT god-role) is deliberately NOT granted
    // here, only CPF_ADMIN, so the legacy authorization model cannot bypass the same maker!=checker
    // boundary SEC-003 enforces at the service layer for these three transitions.
    @PutMapping("/{id}/sanction")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfApplicationResponse sanction(@PathVariable UUID id, @Valid @RequestBody CpfApplicationSanctionRequest request) {
        return applicationService.sanction(id, request);
    }

    @PutMapping("/{id}/disburse")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfApplicationResponse disburse(@PathVariable UUID id) {
        return applicationService.disburse(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfApplicationResponse reject(@PathVariable UUID id) {
        return applicationService.reject(id);
    }
}
