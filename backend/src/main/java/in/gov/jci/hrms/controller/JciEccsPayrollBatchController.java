package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsCollectionSnapshotService;
import in.gov.jci.hrms.service.JciEccsDebitConfirmationService;
import in.gov.jci.hrms.util.DeadlockRetryTemplate;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Payroll<->JCIECCS collection-snapshot integration point (spec section 5). payrollRunId is
 * PayrollBatch.id as a string - JCIECCS itself stores it as an opaque, unvalidated key (no DB FK to
 * payroll_batches, confirmed against the already-live schema), but this controller is where the two
 * modules actually agree on what that string means: this batch's own id.
 */
@RestController
@RequestMapping("/api/jcieccs/payroll/batches/{payrollRunId}")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsPayrollBatchController {

    private final JciEccsCollectionSnapshotService snapshotService;
    private final JciEccsDebitConfirmationService debitConfirmationService;
    private final PayrollBatchRepository payrollBatchRepository;
    private final DeadlockRetryTemplate deadlockRetryTemplate;

    public JciEccsPayrollBatchController(JciEccsCollectionSnapshotService snapshotService,
                                          JciEccsDebitConfirmationService debitConfirmationService, PayrollBatchRepository payrollBatchRepository,
                                          DeadlockRetryTemplate deadlockRetryTemplate) {
        this.snapshotService = snapshotService;
        this.debitConfirmationService = debitConfirmationService;
        this.payrollBatchRepository = payrollBatchRepository;
        this.deadlockRetryTemplate = deadlockRetryTemplate;
    }

    @PostMapping("/snapshot")
    public JciEccsCollectionBatchResponse snapshot(@PathVariable String payrollRunId, Authentication authentication) {
        PayrollBatch payrollBatch = resolvePayrollBatch(payrollRunId);
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return snapshotService.generateSnapshot(payrollRunId, payrollBatch, performedByEmployeeId);
    }

    /** Wrapped in {@link DeadlockRetryTemplate}: this call and a concurrent cash repayment against a loan
     * touched by the same batch line can legitimately acquire their shared rows in different orders under
     * real load, occasionally losing a Postgres deadlock race - always safe to retry, since every effect
     * here is idempotent on the collection_detail's own debitStatus gate. */
    @PostMapping("/confirm-debit")
    public JciEccsCollectionBatchResponse confirmDebit(@PathVariable String payrollRunId,
                                                         @Valid @RequestBody JciEccsDebitConfirmationRequest request, Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return deadlockRetryTemplate.execute(() -> debitConfirmationService.confirmDebit(payrollRunId, request, performedByEmployeeId));
    }

    @GetMapping("/reconciliation")
    public JciEccsCollectionBatchResponse reconciliation(@PathVariable String payrollRunId) {
        return snapshotService.getByPayrollRunId(payrollRunId);
    }

    private PayrollBatch resolvePayrollBatch(String payrollRunId) {
        try {
            return payrollBatchRepository.findById(Long.valueOf(payrollRunId))
                    .orElseThrow(() -> new BusinessRuleViolationException("No payroll batch found with id " + payrollRunId));
        } catch (NumberFormatException notNumeric) {
            throw new BusinessRuleViolationException("payrollRunId must be a numeric payroll batch id, got: " + payrollRunId);
        }
    }
}
