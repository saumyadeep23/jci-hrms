package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanRepayment;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsRepaymentSource;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsReconciliationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JCIECCS Lifecycle Engine Phase 2 - the on-demand administrator integrity checker (spec section 15-16).
 * Deliberately NOT an auto-repair utility and NOT a persisted exception queue (that's
 * {@link JciEccsReconciliationService}, scoped to payroll-run demand/recovery/posting exceptions) - this
 * service answers "is the data internally consistent right now" and returns findings; it never writes
 * anything.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsIntegrityCheckService {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public record IntegrityCheckResult(String checkType, String entityType, Long entityId, Severity severity, String message,
                                        String expectedValue, String actualValue, Instant detectedAt) {
        static IntegrityCheckResult of(String checkType, String entityType, Long entityId, Severity severity, String message,
                                        Object expected, Object actual) {
            return new IntegrityCheckResult(checkType, entityType, entityId, severity, message,
                    String.valueOf(expected), String.valueOf(actual), Instant.now());
        }
    }

    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanRepaymentRepository loanRepaymentRepository;
    private final JciEccsRecoveryRepository recoveryRepository;
    private final JciEccsRecoveryAllocationRepository allocationRepository;
    private final JciEccsReconciliationService reconciliationService;
    private final JciEccsCollectionBatchRepository batchRepository;
    private final JciEccsReconciliationRepository reconciliationRepository;

    public JciEccsIntegrityCheckService(JciEccsLoanRepository loanRepository, JciEccsLoanRepaymentRepository loanRepaymentRepository,
                                         JciEccsRecoveryRepository recoveryRepository, JciEccsRecoveryAllocationRepository allocationRepository,
                                         JciEccsReconciliationService reconciliationService, JciEccsCollectionBatchRepository batchRepository,
                                         JciEccsReconciliationRepository reconciliationRepository) {
        this.loanRepository = loanRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.recoveryRepository = recoveryRepository;
        this.allocationRepository = allocationRepository;
        this.reconciliationService = reconciliationService;
        this.batchRepository = batchRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    /** Loan balance (reuses JciEccsReconciliationService#reconcileLoan rather than re-deriving the same
     * outstanding-principal math a second time), schedule-vs-ledger, and CLOSED-implies-zero-outstanding. */
    public List<IntegrityCheckResult> checkLoan(Long loanId) {
        List<IntegrityCheckResult> results = new ArrayList<>();
        JciEccsLoan loan = loanRepository.findById(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
        var loanRecon = reconciliationService.reconcileLoan(loanId);

        if (loanRecon.outstandingVariance().signum() != 0) {
            results.add(IntegrityCheckResult.of("LOAN_BALANCE", "JCIECCS_LOAN", loanId, Severity.CRITICAL,
                    "Stored outstanding principal does not match ledger-derived outstanding (original - posted recovery + posted reversals)",
                    loanRecon.derivedOutstanding(), loanRecon.storedOutstanding()));
        }
        if (loanRecon.schedulePrincipalRecovered().compareTo(loanRecon.ledgerPrincipalRecovered()) != 0) {
            results.add(IntegrityCheckResult.of("SCHEDULE_RECOVERY", "JCIECCS_LOAN", loanId, Severity.WARNING,
                    "Schedule principal recovered does not match ledger principal recovered",
                    loanRecon.ledgerPrincipalRecovered(), loanRecon.schedulePrincipalRecovered()));
        }
        if (loanRecon.scheduleInterestRecovered().compareTo(loanRecon.ledgerInterestRecovered()) != 0) {
            results.add(IntegrityCheckResult.of("SCHEDULE_RECOVERY", "JCIECCS_LOAN", loanId, Severity.WARNING,
                    "Schedule interest recovered does not match ledger interest recovered",
                    loanRecon.ledgerInterestRecovered(), loanRecon.scheduleInterestRecovered()));
        }
        if (loan.getStatus() == JciEccsLoanStatus.CLOSED && loan.getOutstandingPrincipal().signum() != 0) {
            results.add(IntegrityCheckResult.of("CLOSED_LOAN_BALANCE", "JCIECCS_LOAN", loanId, Severity.CRITICAL,
                    "Loan is CLOSED but outstanding principal is not zero", BigDecimal.ZERO, loan.getOutstandingPrincipal()));
        }
        return results;
    }

    /** Recovery gross = sum(allocations); sum(posted allocations, i.e. every allocation - Phase 1 always
     * posts what it allocates) = the ledger's own posted total for this recovery; reversal validity. */
    public List<IntegrityCheckResult> checkRecovery(Long recoveryId) {
        List<IntegrityCheckResult> results = new ArrayList<>();
        JciEccsRecovery recovery = recoveryRepository.findById(recoveryId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Recovery", recoveryId));
        List<JciEccsRecoveryAllocation> allocations = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recoveryId);
        BigDecimal sumAllocated = allocations.stream().map(JciEccsRecoveryAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<JciEccsLoanRepayment> ledgerRows = loanRepaymentRepository.findByRecovery_Id(recoveryId);
        BigDecimal sumPosted = ledgerRows.stream()
                .map(r -> r.getPrincipalAmount().add(r.getInterestAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        // The THRIFT leg posts to jcieccs_thrift_transaction, not jcieccs_loan_repayment - it has no
        // recovery_id FK of its own to query back by (postThriftContribution only links it via
        // collection_detail_id + a free-text remarks note), but JciEccsRecoveryPostingService always posts
        // the THRIFT allocation's full amount with no partial-application path (unlike TERM/EMERGENCY
        // principal/interest, which the posting-time cap can legitimately reduce - see
        // JciEccsRecoveryPostingService#postLedgerAndBalance's own excessPrincipal handling), so the
        // allocation row's own amount IS what was posted for that leg.
        BigDecimal sumThriftPosted = allocations.stream()
                .filter(a -> a.getComponent() == JciEccsRecoveryComponent.THRIFT)
                .map(JciEccsRecoveryAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        sumPosted = sumPosted.add(sumThriftPosted);

        // grossAmount is what was OFFERED (e.g. the full payroll debit even under an over-debit); allocated
        // is capped at expected. They only need to agree when there was no capping (grossAmount <= what
        // could legitimately be allocated) - a genuine over-debit is Phase 1's own RECONCILIATION_REQUIRED
        // case, already surfaced by JciEccsReconciliationService, so this check only flags allocated vs
        // posted drifting apart, which should never happen given Phase 1's synchronous posting design.
        // A posting-time principal cap (Phase 5 hardening, JciEccsRecoveryPostingService#postLedgerAndBalance)
        // is the one legitimate exception - it always accompanies a RECONCILIATION_REQUIRED recovery status,
        // so it's excluded here rather than flagged as a false integrity violation.
        if (sumAllocated.compareTo(sumPosted) != 0 && recovery.getStatus() != in.gov.jci.hrms.entity.JciEccsRecoveryStatus.RECONCILIATION_REQUIRED) {
            results.add(IntegrityCheckResult.of("RECOVERY_ALLOCATION_POSTING", "JCIECCS_RECOVERY", recoveryId, Severity.CRITICAL,
                    "Sum of allocated amounts does not match sum posted to the ledger", sumAllocated, sumPosted));
        }

        if (recovery.getSource() == JciEccsRecoverySource.REVERSAL) {
            if (recovery.getReversalOfRecovery() == null) {
                results.add(IntegrityCheckResult.of("REVERSAL_REFERENCE", "JCIECCS_RECOVERY", recoveryId, Severity.CRITICAL,
                        "REVERSAL-sourced recovery has no reversalOfRecovery reference", "non-null", "null"));
            } else {
                long timesOriginalReversed = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(recovery.getMember().getId()).stream()
                        .filter(r -> r.getSource() == JciEccsRecoverySource.REVERSAL && r.getReversalOfRecovery() != null
                                && r.getReversalOfRecovery().getId().equals(recovery.getReversalOfRecovery().getId()))
                        .count();
                if (timesOriginalReversed > 1) {
                    results.add(IntegrityCheckResult.of("REVERSAL_DUPLICATE", "JCIECCS_RECOVERY", recovery.getReversalOfRecovery().getId(),
                            Severity.CRITICAL, "Original recovery has been reversed more than once", 1, timesOriginalReversed));
                }
            }
        }
        return results;
    }

    /** Payroll-run demand/recovery/posting - reuses whatever JciEccsReconciliationService already has
     * persisted for this run (running reconcilePayrollRun first if the caller wants a fresh view) rather
     * than re-deriving the same three-way comparison a second time. Every unresolved, non-MATCHED row
     * becomes one finding here. */
    public List<IntegrityCheckResult> checkPayrollCollection(String payrollRunId, boolean refreshFirst, Long performedByEmployeeId) {
        if (refreshFirst) {
            reconciliationService.reconcilePayrollRun(payrollRunId, performedByEmployeeId);
        }
        var batch = batchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Collection Batch", payrollRunId));

        List<IntegrityCheckResult> results = new ArrayList<>();
        for (var row : reconciliationRepository.findByCollectionBatch_IdOrderByMember_MembershipCodeAsc(batch.getId())) {
            if (row.isResolved() || row.getStatus() == in.gov.jci.hrms.entity.JciEccsReconciliationStatus.MATCHED
                    || row.getStatus() == in.gov.jci.hrms.entity.JciEccsReconciliationStatus.POSTING_PENDING) {
                continue;
            }
            Severity severity = switch (row.getStatus()) {
                case POSTING_MISMATCH, ERROR -> Severity.CRITICAL;
                case NOT_RECOVERED, LOCKED_SNAPSHOT_CONFLICT, OVER_RECOVERED -> Severity.WARNING;
                default -> Severity.INFO;
            };
            results.add(IntegrityCheckResult.of(
                    "PAYROLL_COLLECTION_" + row.getStatus(), "JCIECCS_COLLECTION_DETAIL", row.getCollectionDetail().getId(), severity,
                    row.getComponent() + " demand/recovery/posting mismatch for member " + row.getMember().getMembershipCode()
                            + " (" + row.getReasonCode() + ")",
                    row.getExpectedAmount(), row.getPostedAmount()));
        }
        return results;
    }

    /** Sweeps every loan (checkLoan) and every recovery (checkRecovery) - bounded and acceptable for a
     * cooperative credit society's member/loan population; does not touch payroll-run-scoped checks
     * (those are run per payroll run via checkPayrollCollection, since a "full" payroll sweep has no
     * single natural scope without a payroll-run/period selector). */
    public List<IntegrityCheckResult> runFullCheck() {
        List<IntegrityCheckResult> results = new ArrayList<>();
        for (JciEccsLoan loan : loanRepository.findAll()) {
            results.addAll(checkLoan(loan.getId()));
        }
        for (JciEccsRecovery recovery : recoveryRepository.findAll()) {
            results.addAll(checkRecovery(recovery.getId()));
        }
        return results;
    }
}
