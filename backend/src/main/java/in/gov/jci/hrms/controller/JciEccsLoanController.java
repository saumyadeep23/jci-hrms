package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsLoanScheduleResponse;
import in.gov.jci.hrms.dto.JciEccsRestructureRequest;
import in.gov.jci.hrms.dto.JciEccsTopUpRequest;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsLoanService;
import in.gov.jci.hrms.service.JciEccsRestructureService;
import in.gov.jci.hrms.util.DeadlockRetryTemplate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** JCIECCS loan lifecycle (spec section 5) - same COOP_ADMIN/FINANCE_ADMIN/SUPER_ADMIN authorization
 * convention already used by the generic LoanController/PfLedgerController. */
@RestController
@RequestMapping("/api/jcieccs/loans")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsLoanController {

    private final JciEccsLoanService loanService;
    private final JciEccsRestructureService restructureService;
    private final DeadlockRetryTemplate deadlockRetryTemplate;

    public JciEccsLoanController(JciEccsLoanService loanService, JciEccsRestructureService restructureService,
                                  DeadlockRetryTemplate deadlockRetryTemplate) {
        this.loanService = loanService;
        this.restructureService = restructureService;
        this.deadlockRetryTemplate = deadlockRetryTemplate;
    }

    @PostMapping
    public ResponseEntity<JciEccsLoanResponse> createLoan(@Valid @RequestBody JciEccsLoanCreateRequest request,
                                                            Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        String performedByUsername = SecurityUtils.currentUsername(authentication);
        JciEccsLoanResponse created = loanService.createLoan(request, performedByEmployeeId,
                performedByUsername != null ? performedByUsername : "system");
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/jcieccs/loans/" + created.id()))
                .body(created);
    }

    /** Phase 4 - defaults to ACTIVE-only (the "Active Loans" page's own purpose); pass status=ALL for
     * the full history including CLOSED/RESTRUCTURED rows. */
    @GetMapping
    public List<JciEccsLoanResponse> list(@RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return loanService.list("ALL".equalsIgnoreCase(status) ? null : JciEccsLoanStatus.valueOf(status));
    }

    @GetMapping("/{loanId}/schedule")
    public List<JciEccsLoanScheduleResponse> schedule(@PathVariable Long loanId) {
        return loanService.getSchedule(loanId);
    }

    /** Wrapped in {@link DeadlockRetryTemplate}: a concurrent payroll debit confirmation touching the same
     * loan/collection_detail can legitimately acquire their shared rows in different orders under real
     * load, occasionally losing a Postgres deadlock race - always safe to retry, since this call is already
     * idempotent on {@code request.idempotencyKey()}. */
    @PostMapping("/{loanId}/repayments")
    public JciEccsLoanResponse postRepayment(@PathVariable Long loanId, @Valid @RequestBody JciEccsCashRepaymentRequest request,
                                              Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return deadlockRetryTemplate.execute(() -> loanService.postCashRepayment(loanId, request, performedByEmployeeId));
    }

    @PostMapping("/{loanId}/restructure")
    public JciEccsLoanResponse restructure(@PathVariable Long loanId, @Valid @RequestBody JciEccsRestructureRequest request,
                                            Authentication authentication) {
        return restructureService.restructure(loanId, request, SecurityUtils.currentEmployeeId(authentication));
    }

    @PostMapping("/{loanId}/top-up")
    public JciEccsLoanResponse topUp(@PathVariable Long loanId, @Valid @RequestBody JciEccsTopUpRequest request,
                                      Authentication authentication) {
        return restructureService.topUp(loanId, request, SecurityUtils.currentEmployeeId(authentication));
    }
}
