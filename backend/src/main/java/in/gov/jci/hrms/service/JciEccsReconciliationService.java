package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.JciEccsCollectionBatch;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanRepayment;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsReconciliation;
import in.gov.jci.hrms.entity.JciEccsReconciliationReasonCode;
import in.gov.jci.hrms.entity.JciEccsReconciliationResolutionAction;
import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsThriftTransaction;
import in.gov.jci.hrms.entity.JciEccsThriftTransactionType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsReconciliationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
import in.gov.jci.hrms.repository.JciEccsThriftTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JCIECCS Lifecycle Engine Phase 2 - three-way reconciliation (DEMAND vs ACTUAL RECOVERY vs LEDGER
 * POSTING). This service only DETECTS and PERSISTS discrepancies - it never mutates loan/schedule/
 * ledger/thrift state itself (spec section 14/56). {@link #resolveException} records that an
 * authorised action was taken; the only action that actually changes financial state is
 * REVERSE_RECOVERY, which delegates to the already-existing, already-tested
 * {@link JciEccsRecoveryService#reverseRecovery}, never a duplicate/ad-hoc correction path.
 * REQUEST_PAYROLL_CORRECTION is recorded as an audit-trail intent only - there is no Payroll
 * correction API in this codebase to actually call, and none is invented here.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsReconciliationService {

    private static final List<JciEccsRecoveryComponent> ALL_COMPONENTS = List.of(JciEccsRecoveryComponent.THRIFT,
            JciEccsRecoveryComponent.TERM_INTEREST, JciEccsRecoveryComponent.TERM_PRINCIPAL,
            JciEccsRecoveryComponent.EMERGENCY_INTEREST, JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL);

    private final JciEccsCollectionBatchRepository batchRepository;
    private final JciEccsCollectionDetailRepository detailRepository;
    private final JciEccsRecoveryAllocationRepository allocationRepository;
    private final JciEccsLoanRepaymentRepository loanRepaymentRepository;
    private final JciEccsThriftTransactionRepository thriftTransactionRepository;
    private final JciEccsReconciliationRepository reconciliationRepository;
    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final JciEccsRecoveryService recoveryService;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsReconciliationService(JciEccsCollectionBatchRepository batchRepository,
                                         JciEccsCollectionDetailRepository detailRepository, JciEccsRecoveryAllocationRepository allocationRepository,
                                         JciEccsLoanRepaymentRepository loanRepaymentRepository, JciEccsThriftTransactionRepository thriftTransactionRepository,
                                         JciEccsReconciliationRepository reconciliationRepository, JciEccsLoanRepository loanRepository,
                                         JciEccsLoanScheduleRepository scheduleRepository, JciEccsRecoveryService recoveryService,
                                         JciEccsLifecycleEventService lifecycleEventService) {
        this.batchRepository = batchRepository;
        this.detailRepository = detailRepository;
        this.allocationRepository = allocationRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.thriftTransactionRepository = thriftTransactionRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.recoveryService = recoveryService;
        this.lifecycleEventService = lifecycleEventService;
    }

    public record ReconciliationSummary(String payrollRunId, int totalLines, Map<JciEccsReconciliationStatus, Integer> countsByStatus) {
    }

    /** Repeatable: a not-yet-resolved row for the same (collection_detail, component) is refreshed in
     * place (V91's own partial unique index backs this); an already-resolved row is left untouched so a
     * repeat run never silently undoes a recorded resolution. */
    @Transactional
    public ReconciliationSummary reconcilePayrollRun(String payrollRunId, Long performedByEmployeeId) {
        JciEccsCollectionBatch batch = batchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Collection Batch", payrollRunId));
        List<JciEccsCollectionDetail> details = detailRepository.findByBatch_IdOrderByEmployeeIdAsc(batch.getId());

        Map<JciEccsReconciliationStatus, Integer> counts = new EnumMap<>(JciEccsReconciliationStatus.class);
        int totalLines = 0;
        for (JciEccsCollectionDetail detail : details) {
            for (JciEccsRecoveryComponent component : ALL_COMPONENTS) {
                BigDecimal expected = expectedFor(detail, component);
                List<JciEccsRecoveryAllocation> allocations = allocationRepository.findByCollectionDetail_IdAndComponent(detail.getId(), component);
                if (expected.signum() <= 0 && allocations.isEmpty()) {
                    continue; // this member/component pair has nothing to reconcile at all
                }

                BigDecimal actual = netAllocated(allocations);
                BigDecimal posted = computePosted(detail, component);
                boolean wasReversed = allocations.stream().anyMatch(a -> a.getRecovery().getSource() == JciEccsRecoverySource.REVERSAL);
                // The allocation cascade caps every component's own allocated amount at that component's
                // own expected amount (JciEccsRecoveryAllocationService), so a genuine over-debit NEVER
                // shows up as actual > expected on any single component - it only shows up as the
                // recovery's own overall RECONCILIATION_REQUIRED status (Phase 1's excessUnallocated
                // guard). Detect it from the recovery, not from a per-component comparison that can never
                // trigger.
                boolean fromOverDebitRecovery = allocations.stream()
                        .anyMatch(a -> a.getRecovery().getStatus() == in.gov.jci.hrms.entity.JciEccsRecoveryStatus.RECONCILIATION_REQUIRED
                                && a.getRecovery().getSource() != JciEccsRecoverySource.REVERSAL);

                JciEccsReconciliationStatus status = resolveStatus(detail, expected, actual, posted, wasReversed, fromOverDebitRecovery);
                JciEccsReconciliationReasonCode reason = resolveReason(status, detail);

                upsert(payrollRunId, batch, detail, component, expected, actual, posted, status, reason);
                counts.merge(status, 1, Integer::sum);
                totalLines++;
            }
        }

        lifecycleEventService.record("JCIECCS_COLLECTION_BATCH", batch.getId(), "RECONCILIATION_RUN", null, counts, payrollRunId,
                performedByEmployeeId, "Reconciliation run across " + totalLines + " demand line(s)");

        return new ReconciliationSummary(payrollRunId, totalLines, counts);
    }

    private JciEccsReconciliationStatus resolveStatus(JciEccsCollectionDetail detail, BigDecimal expected, BigDecimal actual,
                                                        BigDecimal posted, boolean wasReversed, boolean fromOverDebitRecovery) {
        if (detail.getDebitStatus() == JciEccsDebitStatus.PENDING_DEBIT) {
            return JciEccsReconciliationStatus.POSTING_PENDING;
        }
        if (detail.getDebitStatus() == JciEccsDebitStatus.RECONCILIATION_REQUIRED
                && "CASH_RECOVERY_AFTER_SNAPSHOT_LOCK".equals(detail.getReconciliationReason())) {
            return JciEccsReconciliationStatus.LOCKED_SNAPSHOT_CONFLICT;
        }
        if (fromOverDebitRecovery) {
            return JciEccsReconciliationStatus.OVER_RECOVERED;
        }
        if (wasReversed) {
            return JciEccsReconciliationStatus.REVERSED;
        }
        if (actual.compareTo(posted) != 0) {
            return JciEccsReconciliationStatus.POSTING_MISMATCH; // independently verified against the ledger, not assumed
        }
        if (actual.signum() <= 0 && expected.signum() > 0) {
            return JciEccsReconciliationStatus.NOT_RECOVERED;
        }
        if (actual.compareTo(expected) < 0) {
            return JciEccsReconciliationStatus.PARTIAL;
        }
        if (actual.compareTo(expected) > 0) {
            return JciEccsReconciliationStatus.OVER_RECOVERED;
        }
        return JciEccsReconciliationStatus.MATCHED;
    }

    private JciEccsReconciliationReasonCode resolveReason(JciEccsReconciliationStatus status, JciEccsCollectionDetail detail) {
        return switch (status) {
            case PARTIAL -> JciEccsReconciliationReasonCode.PARTIAL_PAYROLL_DEBIT;
            case NOT_RECOVERED -> JciEccsReconciliationReasonCode.FAILED_PAYROLL_DEBIT;
            case OVER_RECOVERED -> JciEccsReconciliationReasonCode.OVER_DEBIT;
            case POSTING_MISMATCH -> JciEccsReconciliationReasonCode.POSTING_MISMATCH;
            case REVERSED -> JciEccsReconciliationReasonCode.REVERSAL_AFTER_PAYROLL_POSTING;
            case LOCKED_SNAPSHOT_CONFLICT -> JciEccsReconciliationReasonCode.CASH_RECOVERY_AFTER_SNAPSHOT_LOCK;
            case MATCHED, POSTING_PENDING, ERROR -> JciEccsReconciliationReasonCode.UNKNOWN;
        };
    }

    private void upsert(String payrollRunId, JciEccsCollectionBatch batch, JciEccsCollectionDetail detail, JciEccsRecoveryComponent component,
                         BigDecimal expected, BigDecimal actual, BigDecimal posted, JciEccsReconciliationStatus status,
                         JciEccsReconciliationReasonCode reason) {
        var existing = reconciliationRepository.findByCollectionDetail_IdAndComponent(detail.getId(), component);
        if (existing.isPresent()) {
            if (!existing.get().isResolved()) {
                existing.get().refresh(expected, actual, posted, status, reason);
                reconciliationRepository.save(existing.get());
            }
            return;
        }
        reconciliationRepository.save(new JciEccsReconciliation(payrollRunId, batch, detail, detail.getMember(), detail.getEmployeeId(),
                loanFor(component, detail), component, expected, actual, posted, status, reason));
    }

    private BigDecimal expectedFor(JciEccsCollectionDetail detail, JciEccsRecoveryComponent component) {
        return switch (component) {
            case THRIFT -> detail.getThriftAmount();
            case TERM_INTEREST -> detail.getTermInterest();
            case TERM_PRINCIPAL -> BigDecimal.valueOf(detail.getTermPrincipal());
            case EMERGENCY_INTEREST -> detail.getEmergencyInterest();
            case EMERGENCY_PRINCIPAL -> BigDecimal.valueOf(detail.getEmergencyPrincipal());
        };
    }

    private JciEccsLoan loanFor(JciEccsRecoveryComponent component, JciEccsCollectionDetail detail) {
        return switch (component) {
            case THRIFT -> null;
            case TERM_INTEREST, TERM_PRINCIPAL -> detail.getTermLoan();
            case EMERGENCY_INTEREST, EMERGENCY_PRINCIPAL -> detail.getEmergencyLoan();
        };
    }

    /** Net of every non-reversal allocation minus every reversal allocation ever made for this
     * (detail, component) - the true "actually recovered, after any reversal" position. */
    private BigDecimal netAllocated(List<JciEccsRecoveryAllocation> allocations) {
        return allocations.stream()
                .map(a -> a.getRecovery().getSource() == JciEccsRecoverySource.REVERSAL ? a.getAllocatedAmount().negate() : a.getAllocatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Independently derived from the actual ledger (jcieccs_loan_repayment / jcieccs_thrift_transaction),
     * never copied from the allocation row - this is what makes POSTING_MISMATCH a genuine, independently
     * verified check rather than a tautology. */
    private BigDecimal computePosted(JciEccsCollectionDetail detail, JciEccsRecoveryComponent component) {
        if (component == JciEccsRecoveryComponent.THRIFT) {
            return thriftTransactionRepository.findByCollectionDetail_Id(detail.getId()).stream()
                    .map(tx -> tx.getTransactionType() == JciEccsThriftTransactionType.REFUND ? tx.getAmount().negate() : tx.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        boolean isPrincipal = component == JciEccsRecoveryComponent.TERM_PRINCIPAL || component == JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL;
        JciEccsLoan loan = loanFor(component, detail);
        if (loan == null) {
            return BigDecimal.ZERO;
        }
        return loanRepaymentRepository.findByRecovery_CollectionDetail_Id(detail.getId()).stream()
                .filter(r -> r.getLoan().getId().equals(loan.getId()))
                .map(r -> {
                    BigDecimal amount = isPrincipal ? r.getPrincipalAmount() : r.getInterestAmount();
                    return r.getSource() == in.gov.jci.hrms.entity.JciEccsRepaymentSource.REVERSAL ? amount.negate() : amount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record MemberReconciliationSummary(Long memberId, BigDecimal totalExpected, BigDecimal totalActual, BigDecimal totalPosted,
                                                BigDecimal totalVariance, long exceptionCount, List<JciEccsReconciliation> rows) {
    }

    public MemberReconciliationSummary reconcileMember(Long memberId) {
        List<JciEccsReconciliation> rows = reconciliationRepository.findByMember_IdOrderByDetectedAtDesc(memberId);
        BigDecimal totalExpected = rows.stream().map(JciEccsReconciliation::getExpectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActual = rows.stream().map(JciEccsReconciliation::getActualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPosted = rows.stream().map(JciEccsReconciliation::getPostedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVariance = rows.stream().map(JciEccsReconciliation::getVarianceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long exceptionCount = rows.stream().filter(r -> r.getStatus() != JciEccsReconciliationStatus.MATCHED && !r.isResolved()).count();
        return new MemberReconciliationSummary(memberId, totalExpected, totalActual, totalPosted, totalVariance, exceptionCount, rows);
    }

    /** Spec section 8 - Original Principal - Posted Principal Recovery + Posted Principal Reversals =
     * Stored Outstanding, plus schedule-vs-ledger cross-checks. Ephemeral (never persisted) - a live,
     * on-demand loan-level check, distinct from the per-payroll-run rows above which are scoped to one
     * collection_detail and are persisted for exception tracking. */
    public record LoanReconciliationResult(Long loanId, BigDecimal originalPrincipal, BigDecimal postedPrincipalRecovery,
                                             BigDecimal postedPrincipalReversals, BigDecimal derivedOutstanding, BigDecimal storedOutstanding,
                                             BigDecimal outstandingVariance, BigDecimal schedulePrincipalRecovered, BigDecimal ledgerPrincipalRecovered,
                                             BigDecimal scheduleInterestRecovered, BigDecimal ledgerInterestRecovered, JciEccsReconciliationStatus status) {
    }

    public LoanReconciliationResult reconcileLoan(Long loanId) {
        JciEccsLoan loan = loanRepository.findById(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
        List<JciEccsLoanRepayment> ledgerRows = loanRepaymentRepository.findByLoan_IdOrderByRepaymentDateAsc(loanId);

        BigDecimal postedPrincipalRecovery = ledgerRows.stream()
                .filter(r -> r.getSource() != in.gov.jci.hrms.entity.JciEccsRepaymentSource.REVERSAL)
                .map(JciEccsLoanRepayment::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal postedPrincipalReversals = ledgerRows.stream()
                .filter(r -> r.getSource() == in.gov.jci.hrms.entity.JciEccsRepaymentSource.REVERSAL)
                .map(JciEccsLoanRepayment::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ledgerInterestRecoveredNonReversal = ledgerRows.stream()
                .filter(r -> r.getSource() != in.gov.jci.hrms.entity.JciEccsRepaymentSource.REVERSAL)
                .map(JciEccsLoanRepayment::getInterestAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ledgerInterestReversals = ledgerRows.stream()
                .filter(r -> r.getSource() == in.gov.jci.hrms.entity.JciEccsRepaymentSource.REVERSAL)
                .map(JciEccsLoanRepayment::getInterestAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal derivedOutstanding = loan.getDisbursedAmount().subtract(postedPrincipalRecovery).add(postedPrincipalReversals);
        BigDecimal outstandingVariance = derivedOutstanding.subtract(loan.getOutstandingPrincipal());

        List<JciEccsLoanSchedule> schedule = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loanId);
        BigDecimal schedulePrincipalRecovered = schedule.stream().map(JciEccsLoanSchedule::getPrincipalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scheduleInterestRecovered = schedule.stream().map(JciEccsLoanSchedule::getInterestPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ledgerPrincipalRecovered = postedPrincipalRecovery.subtract(postedPrincipalReversals);
        BigDecimal ledgerInterestRecovered = ledgerInterestRecoveredNonReversal.subtract(ledgerInterestReversals);

        boolean matches = outstandingVariance.signum() == 0
                && schedulePrincipalRecovered.compareTo(ledgerPrincipalRecovered) == 0
                && scheduleInterestRecovered.compareTo(ledgerInterestRecovered) == 0;

        return new LoanReconciliationResult(loanId, loan.getDisbursedAmount(), postedPrincipalRecovery, postedPrincipalReversals,
                derivedOutstanding, loan.getOutstandingPrincipal(), outstandingVariance, schedulePrincipalRecovered, ledgerPrincipalRecovered,
                scheduleInterestRecovered, ledgerInterestRecovered, matches ? JciEccsReconciliationStatus.MATCHED : JciEccsReconciliationStatus.POSTING_MISMATCH);
    }

    /**
     * The only entry point that changes anything - and even then, only by delegating to an already-
     * existing, already-audited workflow ({@link JciEccsRecoveryService#reverseRecovery}) when the actor
     * explicitly asks for REVERSE_RECOVERY. Every other action just records the decision. Remarks are
     * mandatory (spec section 13).
     */
    @Transactional
    public JciEccsReconciliation resolveException(Long reconciliationId, JciEccsReconciliationResolutionAction action, String remarks,
                                                    Long performedByEmployeeId) {
        if (remarks == null || remarks.isBlank()) {
            throw new BusinessRuleViolationException("remarks are mandatory to resolve a reconciliation exception");
        }
        JciEccsReconciliation reconciliation = reconciliationRepository.findById(reconciliationId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Reconciliation", reconciliationId));
        if (reconciliation.isResolved()) {
            throw new BusinessRuleViolationException("Reconciliation exception " + reconciliationId + " is already resolved");
        }

        if (action == JciEccsReconciliationResolutionAction.REVERSE_RECOVERY) {
            List<JciEccsRecoveryAllocation> allocations = reconciliation.getCollectionDetail() != null
                    ? allocationRepository.findByCollectionDetail_Id(reconciliation.getCollectionDetail().getId()) : List.of();
            var toReverse = allocations.stream()
                    .filter(a -> a.getRecovery().getSource() != JciEccsRecoverySource.REVERSAL && a.getAllocatedAmount().signum() > 0)
                    .map(JciEccsRecoveryAllocation::getRecovery).findFirst()
                    .orElseThrow(() -> new BusinessRuleViolationException(
                            "No postable recovery found to reverse for reconciliation " + reconciliationId));
            recoveryService.reverseRecovery(toReverse.getId(), remarks, performedByEmployeeId);
        }

        JciEccsReconciliationStatus previousStatus = reconciliation.getStatus();
        reconciliation.resolve(action, remarks, performedByEmployeeId);
        reconciliationRepository.save(reconciliation);

        lifecycleEventService.record("JCIECCS_RECONCILIATION", reconciliation.getId(), "RECONCILIATION_RESOLVED", previousStatus,
                action, null, performedByEmployeeId, remarks);

        return reconciliation;
    }
}
