package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsIntegrityCheckResultResponse;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsIntegrityCheckService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JCIECCS Lifecycle Engine Phase 2 - the on-demand administrator integrity checker (spec section 15-16).
 * Every endpoint here is read-only in effect: it returns findings, it never repairs anything (see
 * {@link JciEccsIntegrityCheckService}'s own javadoc).
 */
@RestController
@RequestMapping("/api/jcieccs/integrity-check")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsIntegrityCheckController {

    private final JciEccsIntegrityCheckService integrityCheckService;

    public JciEccsIntegrityCheckController(JciEccsIntegrityCheckService integrityCheckService) {
        this.integrityCheckService = integrityCheckService;
    }

    @PostMapping("/run")
    public List<JciEccsIntegrityCheckResultResponse> runFullCheck() {
        return toResponses(integrityCheckService.runFullCheck());
    }

    @GetMapping("/loans/{loanId}")
    public List<JciEccsIntegrityCheckResultResponse> checkLoan(@PathVariable Long loanId) {
        return toResponses(integrityCheckService.checkLoan(loanId));
    }

    @GetMapping("/recoveries/{recoveryId}")
    public List<JciEccsIntegrityCheckResultResponse> checkRecovery(@PathVariable Long recoveryId) {
        return toResponses(integrityCheckService.checkRecovery(recoveryId));
    }

    @PostMapping("/payroll-runs/{payrollRunId}/run")
    public List<JciEccsIntegrityCheckResultResponse> checkPayrollCollection(@PathVariable String payrollRunId, Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return toResponses(integrityCheckService.checkPayrollCollection(payrollRunId, true, performedByEmployeeId));
    }

    private List<JciEccsIntegrityCheckResultResponse> toResponses(List<JciEccsIntegrityCheckService.IntegrityCheckResult> results) {
        return results.stream().map(JciEccsIntegrityCheckResultResponse::from).toList();
    }
}
