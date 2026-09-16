package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfDisputeAdminResponse;
import in.gov.jci.hrms.dto.CpfDisputeAssignRequest;
import in.gov.jci.hrms.dto.CpfDisputeHistoryEntryResponse;
import in.gov.jci.hrms.dto.CpfDisputeReviewActionRequest;
import in.gov.jci.hrms.dto.CpfDisputeStartReviewRequest;
import in.gov.jci.hrms.dto.CpfDisputeSummaryResponse;
import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfTransactionDisputeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CPF/HR Reviewer dispute review console (Part 12) - same three-role convention as every other CPF Trust
 * admin controller this session's work has added (CpfInterestRunController/CpfWithdrawalRuleController):
 * reads open to FINANCE_ADMIN/CPF_ADMIN/SUPER_ADMIN, every state-changing action narrowed to CPF_ADMIN/
 * SUPER_ADMIN. This app has no separate "CPF/HR Reviewer" role distinct from CPF_ADMIN today (see
 * SecurityConfig) - Part 20's reviewer/admin distinction collapses onto that single existing role rather
 * than inventing a new one (Part 28: reuse existing RBAC, don't build a parallel mechanism).
 *
 * <h2>Part 13 - reviewer actions never touch the ledger</h2>
 * Every method here only ever calls {@code CpfTransactionDisputeService}, which itself never references
 * (let alone mutates) a single accounting field on the disputed {@code CpfTrustMemberLedgerEntry} - see
 * that service's own javadoc. There is no "correct the transaction" action on this controller by design.
 */
@RestController
@RequestMapping("/api/v1/payroll/trust/cpf/disputes")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfDisputeAdminController {

    private final CpfTransactionDisputeService disputeService;

    public CpfDisputeAdminController(CpfTransactionDisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public Page<CpfDisputeSummaryResponse> search(@RequestParam(required = false) String disputeNumber,
                                                   @RequestParam(required = false) Long employeeId,
                                                   @RequestParam(required = false) CpfDisputeCategory category,
                                                   @RequestParam(required = false) CpfDisputeStatus status,
                                                   Pageable pageable) {
        return disputeService.search(disputeNumber, employeeId, category, status, pageable);
    }

    @GetMapping("/dashboard-counts")
    public Map<CpfDisputeStatus, Long> dashboardCounts() {
        return disputeService.dashboardCounts();
    }

    @GetMapping("/{id}")
    public CpfDisputeAdminResponse get(@PathVariable Long id) {
        return disputeService.getForAdmin(id);
    }

    @GetMapping("/{id}/history")
    public List<CpfDisputeHistoryEntryResponse> history(@PathVariable Long id) {
        return disputeService.history(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfDisputeAdminResponse assign(@PathVariable Long id, @Valid @RequestBody CpfDisputeAssignRequest request,
                                           Authentication authentication) {
        return disputeService.assign(id, request.assigneeEmployeeId(), request.expectedVersion(), actorId(authentication));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfDisputeAdminResponse startReview(@PathVariable Long id, @Valid @RequestBody CpfDisputeStartReviewRequest request,
                                                Authentication authentication) {
        return disputeService.startReview(id, request.expectedVersion(), actorId(authentication));
    }

    @PostMapping("/{id}/request-clarification")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfDisputeAdminResponse requestClarification(@PathVariable Long id, @Valid @RequestBody CpfDisputeReviewActionRequest request,
                                                         Authentication authentication) {
        return disputeService.requestClarification(id, request.remarks(), request.expectedVersion(), actorId(authentication));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfDisputeAdminResponse resolve(@PathVariable Long id, @Valid @RequestBody CpfDisputeReviewActionRequest request,
                                            Authentication authentication) {
        return disputeService.resolve(id, request.remarks(), request.expectedVersion(), actorId(authentication));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfDisputeAdminResponse reject(@PathVariable Long id, @Valid @RequestBody CpfDisputeReviewActionRequest request,
                                           Authentication authentication) {
        return disputeService.reject(id, request.remarks(), request.expectedVersion(), actorId(authentication));
    }

    private Long actorId(Authentication authentication) {
        return SecurityUtils.currentEmployeeId(authentication);
    }
}
