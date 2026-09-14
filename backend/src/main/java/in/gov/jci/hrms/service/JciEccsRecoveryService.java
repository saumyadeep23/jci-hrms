package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsRecoveryStatus;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JCIECCS Lifecycle Engine Phase 1 - the thin orchestrator that wires
 * {@link JciEccsRecoveryAllocationService} (pure allocation math) and {@link JciEccsRecoveryPostingService}
 * (ledger/balance effects) into one transactional unit per recovery event, replacing the previously-fused
 * "callback directly mutates outstanding" pattern. Every public method here is idempotent on its own
 * idempotency key, independent of (and in addition to) whatever gate its caller already applies.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsRecoveryService {

    private final JciEccsRecoveryRepository recoveryRepository;
    private final JciEccsRecoveryAllocationRepository allocationRepository;
    private final JciEccsRecoveryAllocationService allocationService;
    private final JciEccsRecoveryPostingService postingService;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsRecoveryService(JciEccsRecoveryRepository recoveryRepository, JciEccsRecoveryAllocationRepository allocationRepository,
                                   JciEccsRecoveryAllocationService allocationService, JciEccsRecoveryPostingService postingService,
                                   JciEccsLoanScheduleRepository scheduleRepository, JciEccsLifecycleEventService lifecycleEventService) {
        this.recoveryRepository = recoveryRepository;
        this.allocationRepository = allocationRepository;
        this.allocationService = allocationService;
        this.postingService = postingService;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventService = lifecycleEventService;
    }

    public record PayrollRecoveryResult(JciEccsRecovery recovery, BigDecimal totalAllocated) {
    }

    /**
     * Full pipeline for one payroll debit-confirmation line: allocate the actually-debited amount across
     * this employee's due components (Thrift -&gt; Term Interest -&gt; Emergency Interest -&gt; Term Principal
     * -&gt; Emergency Principal), post the allocated portion to the ledger/thrift ledger/outstanding
     * balance, then apply the SCHEDULE effect - the matching installment(s) move to PAID/PARTIAL, or
     * OVERDUE for a loan that had dues this cycle but received nothing. The caller
     * ({@code JciEccsDebitConfirmationService}) remains responsible for {@code collection_detail}'s own
     * {@code debit_status} (Payroll's own reported outcome), which this method never touches.
     */
    @Transactional
    public PayrollRecoveryResult createAndPostPayrollRecovery(JciEccsCollectionDetail detail, BigDecimal actualDebitedAmount,
                                                                String payrollTransactionId, HrmsPayrollCycle cycle, Long performedByEmployeeId) {
        String idempotencyKey = "PAYROLL:" + detail.getId();
        Optional<JciEccsRecovery> existing = recoveryRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            BigDecimal totalAllocated = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(existing.get().getId())
                    .stream().map(JciEccsRecoveryAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PayrollRecoveryResult(existing.get(), totalAllocated);
        }

        Map<JciEccsRecoveryComponent, BigDecimal> expected = new EnumMap<>(JciEccsRecoveryComponent.class);
        expected.put(JciEccsRecoveryComponent.THRIFT, detail.getThriftAmount());
        expected.put(JciEccsRecoveryComponent.TERM_INTEREST, detail.getTermInterest());
        expected.put(JciEccsRecoveryComponent.TERM_PRINCIPAL, BigDecimal.valueOf(detail.getTermPrincipal()));
        expected.put(JciEccsRecoveryComponent.EMERGENCY_INTEREST, detail.getEmergencyInterest());
        expected.put(JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL, BigDecimal.valueOf(detail.getEmergencyPrincipal()));

        JciEccsRecoveryAllocationService.AllocationResult allocationResult = allocationService.allocate(expected, actualDebitedAmount);

        JciEccsRecovery recovery = recoveryRepository.save(new JciEccsRecovery(detail.getMember(), null, JciEccsRecoverySource.PAYROLL,
                actualDebitedAmount, cycle.getPeriodEnd(), cycle, detail, payrollTransactionId, null, idempotencyKey, null,
                "Payroll recovery for cycle " + cycle.getCycleCode(), performedByEmployeeId));

        List<JciEccsRecoveryAllocation> allocations = allocationRepository.saveAll(
                allocationResult.allocations().stream().map(a -> new JciEccsRecoveryAllocation(recovery, detail, loanFor(a.component(), detail),
                        null, a.component(), a.expectedAmount(), a.allocatedAmount(), a.allocationSequence())).toList());

        List<JciEccsRecoveryPostingService.LoanPostingResult> postingResults = actualDebitedAmount.signum() > 0
                ? postingService.postLedgerAndBalance(recovery, allocations, performedByEmployeeId) : List.of();

        applyPayrollScheduleEffect(detail, cycle, postingResults);

        // A posting-time over-recovery (Phase 5 hardening, Scenario C: a concurrent cash repayment already
        // closed out the same principal this payroll line was allocated against) takes priority over the
        // allocation-time status - it's flagged here so the existing over-debit reconciliation check
        // (JciEccsReconciliationService's fromOverDebitRecovery) picks it up on the next reconciliation run.
        boolean postingExceededOutstanding = postingResults.stream().anyMatch(r -> r.excessPrincipal().signum() > 0);
        BigDecimal totalExpected = expected.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        recovery.setStatus(postingExceededOutstanding ? JciEccsRecoveryStatus.RECONCILIATION_REQUIRED
                : resolvePayrollStatus(allocationResult, totalExpected));
        recoveryRepository.save(recovery);

        lifecycleEventService.record("JCIECCS_RECOVERY", recovery.getId(), "RECOVERY_" + recovery.getStatus(), null,
                allocationResult.totalAllocated(), payrollTransactionId, performedByEmployeeId,
                "Payroll recovery for collection detail " + detail.getId());

        return new PayrollRecoveryResult(recovery, allocationResult.totalAllocated());
    }

    private JciEccsRecoveryStatus resolvePayrollStatus(JciEccsRecoveryAllocationService.AllocationResult result, BigDecimal totalExpected) {
        if (result.excessUnallocated().signum() > 0) {
            return JciEccsRecoveryStatus.RECONCILIATION_REQUIRED;
        }
        if (result.totalAllocated().signum() <= 0) {
            return JciEccsRecoveryStatus.FAILED;
        }
        if (result.totalAllocated().compareTo(totalExpected) < 0) {
            return JciEccsRecoveryStatus.PARTIAL;
        }
        return JciEccsRecoveryStatus.POSTED;
    }

    /** Mirrors the pre-Phase-1 settleLoanInstallment: a loan that had dues this cycle but received
     * nothing this round is marked OVERDUE on its schedule row; a loan that received something moves to
     * PAID/PARTIAL depending on whether both principal and interest were fully covered. */
    private void applyPayrollScheduleEffect(JciEccsCollectionDetail detail, HrmsPayrollCycle cycle,
                                             List<JciEccsRecoveryPostingService.LoanPostingResult> postingResults) {
        java.util.Set<Long> postedLoanIds = new java.util.HashSet<>();
        for (var result : postingResults) {
            postedLoanIds.add(result.loan().getId());
            scheduleRepository.findByLoan_IdAndCycle_Id(result.loan().getId(), cycle.getId()).ifPresent(schedule ->
                    updateScheduleAfterPosting(schedule, result.principalPosted(), result.interestPosted()));
        }
        for (JciEccsLoan loan : java.util.Arrays.asList(detail.getTermLoan(), detail.getEmergencyLoan())) {
            if (loan != null && !postedLoanIds.contains(loan.getId())) {
                scheduleRepository.findByLoan_IdAndCycle_Id(loan.getId(), cycle.getId())
                        .ifPresent(schedule -> schedule.setStatus(JciEccsScheduleStatus.OVERDUE));
            }
        }
    }

    private void updateScheduleAfterPosting(JciEccsLoanSchedule schedule, BigDecimal principalPosted, BigDecimal interestPosted) {
        schedule.setPrincipalPaid(schedule.getPrincipalPaid().add(principalPosted));
        schedule.setInterestPaid(schedule.getInterestPaid().add(interestPosted));
        boolean principalFullyPaid = schedule.getPrincipalPaid().compareTo(BigDecimal.valueOf(schedule.getPrincipalDue())) >= 0;
        boolean interestFullyPaid = schedule.getInterestPaid().compareTo(schedule.getInterestDue()) >= 0;
        schedule.setStatus(principalFullyPaid && interestFullyPaid ? JciEccsScheduleStatus.PAID : JciEccsScheduleStatus.PARTIAL);
    }

    private JciEccsLoan loanFor(JciEccsRecoveryComponent component, JciEccsCollectionDetail detail) {
        return switch (component) {
            case THRIFT -> null;
            case TERM_INTEREST, TERM_PRINCIPAL -> detail.getTermLoan();
            case EMERGENCY_INTEREST, EMERGENCY_PRINCIPAL -> detail.getEmergencyLoan();
        };
    }

    /** {@code alreadyPosted} is true on an idempotent replay (matching idempotencyKey already processed) -
     * the caller must not re-run its own schedule-regeneration/closure side effects in that case, since
     * the original call already applied them. */
    public record CashRecoveryResult(JciEccsRecovery recovery, JciEccsLoan loan, boolean alreadyPosted) {
    }

    /**
     * Cash repayment through the same recovery/allocation/posting pipeline. Unlike payroll, a cash
     * receipt is already split by the cashier into principal/interest (no cascade ambiguity) and is, by
     * construction, always fully allocated (JciEccsLoanService already caps principalAmount at the loan's
     * outstanding before calling this) - so it always ends POSTED, never PARTIAL/FAILED. Does not touch
     * the schedule; the caller applies the (unchanged, pre-existing) cash schedule-regeneration logic
     * itself once this returns.
     */
    @Transactional
    public CashRecoveryResult createAndPostCashRecovery(JciEccsLoan loan, BigDecimal principalAmount, BigDecimal interestAmount,
                                                          LocalDate repaymentDate, HrmsPayrollCycle cycle, String referenceId,
                                                          String callerIdempotencyKey, Long performedByEmployeeId) {
        String idempotencyKey = "CASH:" + callerIdempotencyKey;
        Optional<JciEccsRecovery> existing = recoveryRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new CashRecoveryResult(existing.get(), loan, true);
        }

        boolean isTerm = loan.getLoanProduct().getProductCode() == in.gov.jci.hrms.entity.JciEccsLoanProductCode.TERM;
        JciEccsRecoveryComponent principalComponent = isTerm ? JciEccsRecoveryComponent.TERM_PRINCIPAL : JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL;
        JciEccsRecoveryComponent interestComponent = isTerm ? JciEccsRecoveryComponent.TERM_INTEREST : JciEccsRecoveryComponent.EMERGENCY_INTEREST;

        Map<JciEccsRecoveryComponent, BigDecimal> expected = new EnumMap<>(JciEccsRecoveryComponent.class);
        expected.put(principalComponent, principalAmount);
        expected.put(interestComponent, interestAmount);
        BigDecimal grossAmount = principalAmount.add(interestAmount);
        JciEccsRecoveryAllocationService.AllocationResult allocationResult = allocationService.allocate(expected, grossAmount);

        JciEccsRecovery recovery = recoveryRepository.save(new JciEccsRecovery(loan.getMember(), loan, JciEccsRecoverySource.CASH,
                grossAmount, repaymentDate, cycle, null, null, referenceId, idempotencyKey, null, "Cash deposit", performedByEmployeeId));

        List<JciEccsRecoveryAllocation> allocations = allocationRepository.saveAll(
                allocationResult.allocations().stream().map(a -> new JciEccsRecoveryAllocation(recovery, null, loan, null, a.component(),
                        a.expectedAmount(), a.allocatedAmount(), a.allocationSequence())).toList());

        postingService.postLedgerAndBalance(recovery, allocations, performedByEmployeeId);
        recovery.setStatus(JciEccsRecoveryStatus.POSTED);
        recoveryRepository.save(recovery);

        lifecycleEventService.record("JCIECCS_RECOVERY", recovery.getId(), "CASH_REPAYMENT_POSTED", null, grossAmount, referenceId,
                performedByEmployeeId, "Cash deposit against loan " + loan.getLoanIssueId());

        return new CashRecoveryResult(recovery, loan, false);
    }

    /**
     * Compensating reversal (spec section 21-22): never deletes or mutates the original recovery/ledger
     * rows. Restores schedule + outstanding via {@link JciEccsRecoveryPostingService#postReversalLedgerAndBalance}
     * and marks the original REVERSED. A recovery can only be reversed once (idempotency key +
     * explicit status check).
     */
    @Transactional
    public JciEccsRecovery reverseRecovery(Long recoveryId, String reason, Long performedByEmployeeId) {
        // Pessimistic lock (Phase 5 hardening): serializes concurrent/duplicate reversal requests for the
        // same recovery so the loser sees the committed REVERSED status and fails cleanly, rather than
        // racing on the REVERSAL: idempotency key's unique constraint.
        JciEccsRecovery original = recoveryRepository.findByIdForUpdate(recoveryId)
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Recovery", recoveryId));
        if (original.getSource() == JciEccsRecoverySource.REVERSAL) {
            throw new BusinessRuleViolationException("Recovery " + recoveryId + " is itself a reversal and cannot be reversed");
        }
        if (original.getStatus() != JciEccsRecoveryStatus.POSTED && original.getStatus() != JciEccsRecoveryStatus.PARTIAL) {
            throw new BusinessRuleViolationException(
                    "Recovery " + recoveryId + " must be POSTED or PARTIAL to reverse but is " + original.getStatus());
        }

        String idempotencyKey = "REVERSAL:" + recoveryId;
        Optional<JciEccsRecovery> existingReversal = recoveryRepository.findByIdempotencyKey(idempotencyKey);
        if (existingReversal.isPresent()) {
            return existingReversal.get();
        }

        List<JciEccsRecoveryAllocation> originalAllocations = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recoveryId);
        JciEccsRecovery reversal = recoveryRepository.save(new JciEccsRecovery(original.getMember(), original.getLoan(),
                JciEccsRecoverySource.REVERSAL, original.getGrossAmount(), LocalDate.now(), original.getCycle(),
                original.getCollectionDetail(), null, null, idempotencyKey, original, reason, performedByEmployeeId));

        List<JciEccsRecoveryAllocation> reversalAllocations = allocationRepository.saveAll(originalAllocations.stream()
                .map(a -> new JciEccsRecoveryAllocation(reversal, a.getCollectionDetail(), a.getLoan(), a.getLoanSchedule(), a.getComponent(),
                        a.getAllocatedAmount(), a.getAllocatedAmount(), a.getAllocationSequence()))
                .toList());

        List<JciEccsRecoveryPostingService.LoanPostingResult> reversalPostings =
                postingService.postReversalLedgerAndBalance(reversal, original, reversalAllocations, performedByEmployeeId);

        for (var result : reversalPostings) {
            scheduleRepository.findByLoan_IdAndCycle_Id(result.loan().getId(),
                            original.getCycle() != null ? original.getCycle().getId() : -1L)
                    .ifPresent(schedule -> restoreScheduleAfterReversal(schedule, result.principalPosted(), result.interestPosted()));
        }

        reversal.setStatus(JciEccsRecoveryStatus.POSTED);
        recoveryRepository.save(reversal);
        original.setStatus(JciEccsRecoveryStatus.REVERSED);
        original.setReversedAt(java.time.Instant.now());
        recoveryRepository.save(original);

        lifecycleEventService.record("JCIECCS_RECOVERY", original.getId(), "RECOVERY_REVERSED", null, reversal.getId(), null,
                performedByEmployeeId, reason);

        return reversal;
    }

    private void restoreScheduleAfterReversal(JciEccsLoanSchedule schedule, BigDecimal principalReversed, BigDecimal interestReversed) {
        schedule.setPrincipalPaid(schedule.getPrincipalPaid().subtract(principalReversed).max(BigDecimal.ZERO));
        schedule.setInterestPaid(schedule.getInterestPaid().subtract(interestReversed).max(BigDecimal.ZERO));
        boolean principalFullyPaid = schedule.getPrincipalPaid().compareTo(BigDecimal.valueOf(schedule.getPrincipalDue())) >= 0;
        boolean interestFullyPaid = schedule.getInterestPaid().compareTo(schedule.getInterestDue()) >= 0;
        if (principalFullyPaid && interestFullyPaid) {
            schedule.setStatus(JciEccsScheduleStatus.PAID);
        } else if (schedule.getPrincipalPaid().signum() > 0 || schedule.getInterestPaid().signum() > 0) {
            schedule.setStatus(JciEccsScheduleStatus.PARTIAL);
        } else {
            schedule.setStatus(JciEccsScheduleStatus.DUE);
        }
    }
}
