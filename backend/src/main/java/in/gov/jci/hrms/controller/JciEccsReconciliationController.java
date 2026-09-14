package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsLoanReconciliationResponse;
import in.gov.jci.hrms.dto.JciEccsReconciliationResponse;
import in.gov.jci.hrms.dto.JciEccsReconciliationSummaryResponse;
import in.gov.jci.hrms.dto.JciEccsResolveReconciliationRequest;
import in.gov.jci.hrms.entity.JciEccsReconciliation;
import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsReconciliationRepository;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsReconciliationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JCIECCS Lifecycle Engine Phase 2 - three-way reconciliation (DEMAND vs ACTUAL RECOVERY vs LEDGER
 * POSTING). Separate namespace from the existing /api/jcieccs/payroll/batches/{payrollRunId}/reconciliation
 * endpoint (JciEccsPayrollBatchController), which merely echoes the collection batch's own snapshot
 * fields - this controller exposes the genuine three-way comparison, never duplicating that route.
 */
@RestController
@RequestMapping("/api/jcieccs/reconciliation")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsReconciliationController {

    private final JciEccsReconciliationService reconciliationService;
    private final JciEccsReconciliationRepository reconciliationRepository;
    private final JciEccsCollectionBatchRepository batchRepository;

    public JciEccsReconciliationController(JciEccsReconciliationService reconciliationService,
                                            JciEccsReconciliationRepository reconciliationRepository, JciEccsCollectionBatchRepository batchRepository) {
        this.reconciliationService = reconciliationService;
        this.reconciliationRepository = reconciliationRepository;
        this.batchRepository = batchRepository;
    }

    /** Every unresolved, non-MATCHED exception across every payroll run - the default operational view. */
    @GetMapping
    public List<JciEccsReconciliationResponse> unresolvedExceptions() {
        return reconciliationRepository.findByStatusNotAndResolvedFalseOrderByDetectedAtDesc(JciEccsReconciliationStatus.MATCHED).stream()
                .map(JciEccsReconciliationResponse::from).toList();
    }

    @GetMapping("/payroll-runs/{payrollRunId}")
    public List<JciEccsReconciliationResponse> forPayrollRun(@PathVariable String payrollRunId) {
        var batch = batchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new in.gov.jci.hrms.exception.MasterDataNotFoundException("JCIECCS Collection Batch", payrollRunId));
        return reconciliationRepository.findByCollectionBatch_IdOrderByMember_MembershipCodeAsc(batch.getId()).stream()
                .map(JciEccsReconciliationResponse::from).toList();
    }

    @PostMapping("/payroll-runs/{payrollRunId}/run")
    public JciEccsReconciliationSummaryResponse run(@PathVariable String payrollRunId, Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return JciEccsReconciliationSummaryResponse.from(reconciliationService.reconcilePayrollRun(payrollRunId, performedByEmployeeId));
    }

    @GetMapping("/members/{memberId}")
    public List<JciEccsReconciliationResponse> forMember(@PathVariable Long memberId) {
        return reconciliationService.reconcileMember(memberId).rows().stream().map(JciEccsReconciliationResponse::from).toList();
    }

    @GetMapping("/loans/{loanId}")
    public JciEccsLoanReconciliationResponse forLoan(@PathVariable Long loanId) {
        return JciEccsLoanReconciliationResponse.from(reconciliationService.reconcileLoan(loanId));
    }

    @PostMapping("/{id}/resolve")
    public JciEccsReconciliationResponse resolve(@PathVariable Long id, @Valid @RequestBody JciEccsResolveReconciliationRequest request,
                                                   Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        JciEccsReconciliation resolved = reconciliationService.resolveException(id, request.action(), request.remarks(), performedByEmployeeId);
        return JciEccsReconciliationResponse.from(resolved);
    }
}
