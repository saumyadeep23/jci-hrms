package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanRepayment;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRepaymentSource;
import in.gov.jci.hrms.entity.JciEccsThriftTransaction;
import in.gov.jci.hrms.entity.JciEccsThriftTransactionSource;
import in.gov.jci.hrms.entity.JciEccsThriftTransactionType;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsThriftTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JCIECCS Lifecycle Engine Phase 1 - turns a persisted {@link JciEccsRecovery} + its
 * {@link JciEccsRecoveryAllocation} rows into the actual LEDGER POSTING and BALANCE UPDATE: one
 * {@link JciEccsLoanRepayment} row per loan touched (matching the pre-Phase-1 code's own
 * one-row-per-loan-per-event granularity), one {@link JciEccsThriftTransaction} row if a THRIFT
 * allocation exists, and a reduction of {@code loan.outstandingPrincipal} by exactly the posted
 * PRINCIPAL allocation - never gross amount, never interest, never thrift (spec section 10). Never
 * accepts a client-supplied outstanding balance; every balance effect is derived from the allocation
 * rows it was given. Deliberately does NOT touch {@link in.gov.jci.hrms.entity.JciEccsLoanSchedule} -
 * the payroll path's per-installment schedule update and the cash path's schedule-regeneration are
 * genuinely different, already-correct mechanics, applied by their respective callers
 * ({@code JciEccsRecoveryService}) after this service returns.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsRecoveryPostingService {

    private final JciEccsLoanRepository loanRepository;
    private final JciEccsThriftTransactionRepository thriftTransactionRepository;
    private final JciEccsLoanRepaymentRepository loanRepaymentRepository;
    private final JciEccsMemberRepository memberRepository;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsRecoveryPostingService(JciEccsLoanRepository loanRepository,
                                          JciEccsThriftTransactionRepository thriftTransactionRepository,
                                          JciEccsLoanRepaymentRepository loanRepaymentRepository,
                                          JciEccsMemberRepository memberRepository,
                                          JciEccsLifecycleEventService lifecycleEventService) {
        this.loanRepository = loanRepository;
        this.thriftTransactionRepository = thriftTransactionRepository;
        this.loanRepaymentRepository = loanRepaymentRepository;
        this.memberRepository = memberRepository;
        this.lifecycleEventService = lifecycleEventService;
    }

    /** {@code excessPrincipal} is positive only when {@code principalPosted} exceeded the loan's own
     * current outstanding at posting time - always zero for cash (which caps against outstanding under
     * the same lock before ever reaching here), but genuinely possible for payroll: the allocation was
     * computed against a snapshot/expected amount that can go stale if a cash repayment closes out the
     * same due principal first (Phase 5 hardening, Scenario C). The ledger still records the true
     * {@code principalPosted} amount actually collected (an external, factual deduction), but the BALANCE
     * effect is capped so outstandingPrincipal can never go negative - the caller must flag the recovery
     * RECONCILIATION_REQUIRED when this is positive, surfaced by the existing over-debit reconciliation
     * check ({@code JciEccsReconciliationService}'s fromOverDebitRecovery). */
    public record LoanPostingResult(JciEccsLoan loan, BigDecimal principalPosted, BigDecimal interestPosted, boolean closedNow,
                                     BigDecimal excessPrincipal) {
    }

    @Transactional
    public List<LoanPostingResult> postLedgerAndBalance(JciEccsRecovery recovery, List<JciEccsRecoveryAllocation> allocations,
                                                          Long performedByEmployeeId) {
        allocations.stream()
                .filter(a -> a.getComponent() == JciEccsRecoveryComponent.THRIFT && a.getAllocatedAmount().signum() > 0)
                .findFirst()
                .ifPresent(thriftAllocation -> postThriftContribution(recovery, thriftAllocation, JciEccsThriftTransactionType.CONTRIBUTION));

        List<LoanPostingResult> results = new ArrayList<>();
        for (var entry : groupByLoan(allocations).entrySet()) {
            List<JciEccsRecoveryAllocation> loanAllocations = entry.getValue();
            BigDecimal principalPosted = sumComponents(loanAllocations, JciEccsRecoveryComponent.TERM_PRINCIPAL, JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL);
            BigDecimal interestPosted = sumComponents(loanAllocations, JciEccsRecoveryComponent.TERM_INTEREST, JciEccsRecoveryComponent.EMERGENCY_INTEREST);
            if (principalPosted.signum() <= 0 && interestPosted.signum() <= 0) {
                continue;
            }

            JciEccsLoan loan = loanRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", entry.getKey()));

            JciEccsRecoveryAllocation linkedAllocation = loanAllocations.size() == 1 ? loanAllocations.get(0) : null;
            postLoanLedgerRow(loan, recovery, linkedAllocation, principalPosted, interestPosted, null);

            // Cap the BALANCE effect at the loan's current (freshly-locked) outstanding - never the ledger
            // fact above, which must keep recording the true amount actually collected. principalToApply
            // can be less than principalPosted only when a concurrent recovery (typically cash) already
            // reduced outstanding below what this allocation was computed against (Phase 5 hardening).
            BigDecimal currentOutstanding = loan.getOutstandingPrincipal();
            BigDecimal principalToApply = principalPosted.min(currentOutstanding).max(BigDecimal.ZERO);
            BigDecimal excessPrincipal = principalPosted.subtract(principalToApply);

            loan.setOutstandingPrincipal(currentOutstanding.subtract(principalToApply));
            boolean closedNow = false;
            if (loan.getOutstandingPrincipal().signum() <= 0 && loan.getStatus() == JciEccsLoanStatus.ACTIVE) {
                loan.setOutstandingPrincipal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                loan.setStatus(JciEccsLoanStatus.CLOSED);
                loan.setClosedDate(recovery.getPaymentDate());
                loan.setClosureReason("Fully repaid via " + recovery.getSource() + " recovery #" + recovery.getId());
                closedNow = true;
            }

            lifecycleEventService.record("JCIECCS_LOAN", loan.getId(), "REPAYMENT_POSTED", null,
                    new RepaymentAudit(principalPosted, interestPosted, loan.getOutstandingPrincipal()),
                    recovery.getPayrollTransactionReference() != null ? recovery.getPayrollTransactionReference() : recovery.getReceiptNumber(),
                    performedByEmployeeId, "Recovery #" + recovery.getId() + " (" + recovery.getSource() + ")");

            results.add(new LoanPostingResult(loan, principalPosted, interestPosted, closedNow, excessPrincipal));
        }

        recovery.setPostingDate(LocalDate.now());
        recovery.setPostedAt(Instant.now());
        return results;
    }

    /** Compensating entries for a reversal - restores outstandingPrincipal by ADDING BACK the reversed
     * principal, posts positive-magnitude REVERSAL-source ledger rows linked via reversalOf to the exact
     * original row(s) {@code originalRecovery} produced, and a thrift REFUND (the existing schema's own
     * "decreases balance" transaction type - no schema change needed) if the original touched thrift.
     * Never mutates or deletes the original rows. */
    @Transactional
    public List<LoanPostingResult> postReversalLedgerAndBalance(JciEccsRecovery reversalRecovery, JciEccsRecovery originalRecovery,
                                                                  List<JciEccsRecoveryAllocation> reversalAllocations, Long performedByEmployeeId) {
        reversalAllocations.stream()
                .filter(a -> a.getComponent() == JciEccsRecoveryComponent.THRIFT && a.getAllocatedAmount().signum() > 0)
                .findFirst()
                .ifPresent(thriftAllocation -> postThriftContribution(reversalRecovery, thriftAllocation, JciEccsThriftTransactionType.REFUND));

        Map<Long, JciEccsLoanRepayment> originalLedgerByLoan = loanRepaymentRepository.findByRecovery_Id(originalRecovery.getId())
                .stream().collect(Collectors.toMap(r -> r.getLoan().getId(), r -> r, (a, b) -> a));

        List<LoanPostingResult> results = new ArrayList<>();
        for (var entry : groupByLoan(reversalAllocations).entrySet()) {
            List<JciEccsRecoveryAllocation> loanAllocations = entry.getValue();
            BigDecimal principalReversed = sumComponents(loanAllocations, JciEccsRecoveryComponent.TERM_PRINCIPAL, JciEccsRecoveryComponent.EMERGENCY_PRINCIPAL);
            BigDecimal interestReversed = sumComponents(loanAllocations, JciEccsRecoveryComponent.TERM_INTEREST, JciEccsRecoveryComponent.EMERGENCY_INTEREST);
            if (principalReversed.signum() <= 0 && interestReversed.signum() <= 0) {
                continue;
            }

            JciEccsLoan loan = loanRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", entry.getKey()));
            JciEccsRecoveryAllocation linkedAllocation = loanAllocations.size() == 1 ? loanAllocations.get(0) : null;
            JciEccsLoanRepayment originalLedgerRow = originalLedgerByLoan.get(loan.getId());

            postLoanLedgerRow(loan, reversalRecovery, linkedAllocation, principalReversed, interestReversed, originalLedgerRow);

            // A loan reopened by a reversal of its closing payment returns to ACTIVE - closure is derived
            // from outstanding=0, and a reversal is precisely the case where that's no longer true.
            if (loan.getStatus() == JciEccsLoanStatus.CLOSED) {
                loan.setStatus(JciEccsLoanStatus.ACTIVE);
                loan.setClosedDate(null);
                loan.setClosureReason(null);
            }
            loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().add(principalReversed));

            lifecycleEventService.record("JCIECCS_LOAN", loan.getId(), "RECOVERY_REVERSED", null,
                    new RepaymentAudit(principalReversed, interestReversed, loan.getOutstandingPrincipal()),
                    reversalRecovery.getRemarks(), performedByEmployeeId,
                    "Reversal of recovery #" + originalRecovery.getId());

            results.add(new LoanPostingResult(loan, principalReversed, interestReversed, false, BigDecimal.ZERO));
        }

        reversalRecovery.setPostingDate(LocalDate.now());
        reversalRecovery.setPostedAt(Instant.now());
        return results;
    }

    private void postLoanLedgerRow(JciEccsLoan loan, JciEccsRecovery recovery, JciEccsRecoveryAllocation linkedAllocation,
                                    BigDecimal principalAmount, BigDecimal interestAmount, JciEccsLoanRepayment reversalOf) {
        JciEccsRepaymentSource source = switch (recovery.getSource()) {
            case PAYROLL -> JciEccsRepaymentSource.PAYROLL;
            case CASH -> JciEccsRepaymentSource.CASH;
            case REVERSAL -> JciEccsRepaymentSource.REVERSAL;
        };
        String referenceId = recovery.getPayrollTransactionReference() != null ? recovery.getPayrollTransactionReference() : recovery.getReceiptNumber();
        JciEccsLoanRepayment repayment = new JciEccsLoanRepayment(loan, null, recovery.getCollectionDetail(),
                loan.getMember().getEmployeeId(), recovery.getPaymentDate(), recovery.getCycle(), source, referenceId,
                principalAmount, interestAmount, "Recovery #" + recovery.getId() + (reversalOf != null ? " (reversal)" : ""));
        repayment.linkRecovery(recovery, linkedAllocation, reversalOf);
        loanRepaymentRepository.save(repayment);
    }

    private void postThriftContribution(JciEccsRecovery recovery, JciEccsRecoveryAllocation thriftAllocation, JciEccsThriftTransactionType type) {
        // Locks the member row before the read-latest-balance/insert-next-row sequence below so two
        // concurrent thrift-affecting recoveries for the same member can never compute newBalance off the
        // same stale previousBalance (Phase 5 hardening - see JciEccsMemberRepository.findByIdForUpdate).
        JciEccsMember member = memberRepository.findByIdForUpdate(recovery.getMember().getId())
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Member", recovery.getMember().getId()));
        BigDecimal previousBalance = thriftTransactionRepository.findTopByMember_IdOrderByIdDesc(member.getId())
                .map(JciEccsThriftTransaction::getBalanceAfter).orElse(BigDecimal.ZERO);
        BigDecimal newBalance = type == JciEccsThriftTransactionType.REFUND
                ? previousBalance.subtract(thriftAllocation.getAllocatedAmount())
                : previousBalance.add(thriftAllocation.getAllocatedAmount());
        JciEccsThriftTransactionSource source = recovery.getSource() == in.gov.jci.hrms.entity.JciEccsRecoverySource.CASH
                ? JciEccsThriftTransactionSource.CASH : JciEccsThriftTransactionSource.PAYROLL;
        thriftTransactionRepository.save(new JciEccsThriftTransaction(member, member.getEmployeeId(), recovery.getCollectionDetail(),
                recovery.getPaymentDate(), recovery.getCycle(), type, thriftAllocation.getAllocatedAmount(), source, newBalance,
                "Recovery #" + recovery.getId()));
    }

    private Map<Long, List<JciEccsRecoveryAllocation>> groupByLoan(List<JciEccsRecoveryAllocation> allocations) {
        return allocations.stream().filter(a -> a.getLoan() != null).collect(Collectors.groupingBy(a -> a.getLoan().getId()));
    }

    private BigDecimal sumComponents(List<JciEccsRecoveryAllocation> allocations, JciEccsRecoveryComponent... components) {
        List<JciEccsRecoveryComponent> wanted = List.of(components);
        return allocations.stream().filter(a -> wanted.contains(a.getComponent())).map(JciEccsRecoveryAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record RepaymentAudit(BigDecimal principalAmount, BigDecimal interestAmount, BigDecimal outstandingAfter) {
    }
}
