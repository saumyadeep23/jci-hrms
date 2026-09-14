package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationLineRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.entity.JciEccsCollectionBatch;
import in.gov.jci.hrms.entity.JciEccsCollectionBatchStatus;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consumes Payroll's async post-disbursement debit-confirmation callback (spec section 1.6) and, per
 * line, drives it through the recovery/allocation/posting pipeline ({@link JciEccsRecoveryService}) -
 * this service itself no longer computes allocation or mutates the ledger/loan balance directly (JCIECCS
 * Lifecycle Engine Phase 1). Each line is idempotent: a collection_detail already past PENDING_DEBIT is
 * skipped rather than re-posted, so a retried callback for an already-processed batch is safe;
 * {@link JciEccsRecoveryService} additionally guards on its own idempotency key as a second, independent
 * backstop.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsDebitConfirmationService {

    private final JciEccsCollectionBatchRepository batchRepository;
    private final JciEccsCollectionDetailRepository detailRepository;
    private final JciEccsRecoveryService recoveryService;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsDebitConfirmationService(JciEccsCollectionBatchRepository batchRepository,
                                            JciEccsCollectionDetailRepository detailRepository, JciEccsRecoveryService recoveryService,
                                            JciEccsLifecycleEventService lifecycleEventService) {
        this.batchRepository = batchRepository;
        this.detailRepository = detailRepository;
        this.recoveryService = recoveryService;
        this.lifecycleEventService = lifecycleEventService;
    }

    @Transactional
    public JciEccsCollectionBatchResponse confirmDebit(String payrollRunId, JciEccsDebitConfirmationRequest request, Long performedByEmployeeId) {
        JciEccsCollectionBatch batch = batchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Collection Batch", payrollRunId));

        for (JciEccsDebitConfirmationLineRequest line : request.lines()) {
            processLine(batch, line, performedByEmployeeId);
        }

        BigDecimal totalDebited = detailRepository.findByBatch_IdOrderByEmployeeIdAsc(batch.getId()).stream()
                .map(JciEccsCollectionDetail::getActualDebitedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        batch.setTotalDebitedAmount(totalDebited);
        batch.setBatchStatus(JciEccsCollectionBatchStatus.PROCESSED);
        batchRepository.save(batch);

        lifecycleEventService.record("JCIECCS_COLLECTION_BATCH", batch.getId(), "DEBIT_CONFIRMED", null, totalDebited,
                payrollRunId, performedByEmployeeId, "Debit confirmation processed for " + request.lines().size() + " employee(s)");

        List<in.gov.jci.hrms.dto.JciEccsCollectionDetailResponse> details = detailRepository.findByBatch_IdOrderByEmployeeIdAsc(batch.getId())
                .stream().map(in.gov.jci.hrms.dto.JciEccsCollectionDetailResponse::from).toList();
        return JciEccsCollectionBatchResponse.from(batch, details);
    }

    private void processLine(JciEccsCollectionBatch batch, JciEccsDebitConfirmationLineRequest line, Long performedByEmployeeId) {
        // Pessimistic lock, not a plain read: two concurrent/duplicate callbacks for the same line must
        // serialize here so the loser observes the already-updated debitStatus after the winner commits,
        // rather than both racing past this check with the stale PENDING_DEBIT snapshot (Phase 5 hardening).
        JciEccsCollectionDetail detail = detailRepository.findByBatch_IdAndEmployeeIdForUpdate(batch.getId(), line.employeeId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No collection detail for employee " + line.employeeId() + " in batch " + batch.getId()));

        if (detail.getDebitStatus() != JciEccsDebitStatus.PENDING_DEBIT) {
            return; // already processed - idempotent retry
        }

        // detail.getTotalSnapshotAmount() (a DB GENERATED ALWAYS column) is never populated on this
        // still-managed entity within the same persistence context that created it earlier in this
        // transaction (generateSnapshot's save is never refreshed) - compute the same total from its
        // own components instead of reading the stale/null generated value back.
        BigDecimal totalSnapshotAmount = detail.getThriftAmount()
                .add(BigDecimal.valueOf(detail.getTermPrincipal())).add(detail.getTermInterest())
                .add(BigDecimal.valueOf(detail.getEmergencyPrincipal())).add(detail.getEmergencyInterest());

        BigDecimal amountToAllocate = switch (line.outcome()) {
            case DEBIT_SUCCESS -> totalSnapshotAmount;
            case DEBIT_PARTIAL -> requirePositiveAmount(line);
            case DEBIT_FAILED -> BigDecimal.ZERO;
            case PENDING_DEBIT, REVERSED, RECONCILIATION_REQUIRED -> throw new BusinessRuleViolationException(
                    "outcome must be DEBIT_SUCCESS, DEBIT_PARTIAL or DEBIT_FAILED, got " + line.outcome());
        };

        var result = recoveryService.createAndPostPayrollRecovery(detail, amountToAllocate, line.payrollTransactionId(), batch.getCycle(),
                performedByEmployeeId);

        detail.setDebitStatus(line.outcome());
        detail.setActualDebitedAmount(result.totalAllocated());
        detail.setPayrollTransactionId(line.payrollTransactionId());
        detail.setPostedAt(java.time.Instant.now());
        detailRepository.save(detail);
    }

    private BigDecimal requirePositiveAmount(JciEccsDebitConfirmationLineRequest line) {
        if (line.actualDebitedAmount() == null || line.actualDebitedAmount().signum() <= 0) {
            throw new BusinessRuleViolationException("DEBIT_PARTIAL requires a positive actualDebitedAmount for employee " + line.employeeId());
        }
        return line.actualDebitedAmount();
    }
}
