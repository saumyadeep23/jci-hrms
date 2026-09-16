package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationLineRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsRestructureRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsRecoveryStatus;
import in.gov.jci.hrms.entity.JciEccsThriftTransaction;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryRepository;
import in.gov.jci.hrms.repository.JciEccsThriftTransactionRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.util.DeadlockRetryTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCIECCS Lifecycle Engine Phase 5 hardening - real, genuinely concurrent (two OS threads, two separate
 * transactions/connections) races against the recovery/allocation/posting/reversal pipeline, mirroring
 * {@link CpfLoanConcurrencyTest}'s own pattern (see its class javadoc for why this is deliberately NOT
 * @Transactional at the class level: fixtures are committed in setUp() and cleaned up explicitly in
 * tearDown(), since a Spring-managed test transaction is bound only to the main thread and would be
 * invisible to the worker threads' own separate connections).
 */
@SpringBootTest
class JciEccsConcurrencyHardeningTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsDebitConfirmationService debitConfirmationService;
    @Autowired private JciEccsRecoveryService recoveryService;
    @Autowired private JciEccsRestructureService restructureService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private JciEccsLoanScheduleRepository scheduleRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private JciEccsRecoveryRepository recoveryRepository;
    @Autowired private JciEccsRecoveryAllocationRepository allocationRepository;
    @Autowired private JciEccsLoanRepaymentRepository loanRepaymentRepository;
    @Autowired private JciEccsCollectionDetailRepository collectionDetailRepository;
    @Autowired private JciEccsThriftTransactionRepository thriftTransactionRepository;
    @Autowired private DeadlockRetryTemplate deadlockRetryTemplate;

    // JciEccsCollectionSnapshotService.generateSnapshot is a whole-of-cooperative operation - it snapshots
    // EVERY currently-ACTIVE JciEccsMember in the database into the one JciEccsCollectionBatch for a given
    // (month, year), not just the member passed in. Against this dev database that batch ends up shared
    // with 100+ pre-existing real members the moment it's created. Two consequences drive the design below:
    // (1) tearDown must NEVER delete the batch or iterate "every collection_detail in the batch" - only the
    // one row belonging to this test's own employee, or it would destroy unrelated real data; (2) the
    // (month, year) must be fresh on every single invocation (never reused, never deleted), since a repeat
    // generateSnapshot call for an existing payrollRunId is itself idempotent and returns the frozen
    // original snapshot verbatim - a member created after that point would simply never be included.
    // hrms_payroll_cycle.cycle_code is VARCHAR(7) ("YYYY-MM") - the year component must stay 4 digits.
    private static final AtomicInteger CYCLE_SEQ = new AtomicInteger((int) (System.currentTimeMillis() % 90_000));

    private Department department;
    private Designation designation;
    private Employee employee;
    private JciEccsMember member;
    private PayrollBatch payrollBatch;
    private String payrollRunId;
    private Long loanId;
    // Suffix for every per-scenario receipt/transaction/idempotency-key literal below (e.g. "TXN-RACE-D2-<seq>",
    // "IDEMP-RACE-D2-<seq>") - a fixed literal collides forever against any leftover row a previous, incompletely
    // torn-down run left behind (jcieccs_recovery.idempotency_key never expires and is checked before any balance
    // effect is applied), permanently masking this scenario's own repayment/reversal from ever taking effect.
    private int seq;
    private int cycleYear;
    private int cycleMonth;
    private LocalDate disbursementDate;

    @BeforeEach
    void setUp() {
        // seq itself (not cycleYear, which only changes once every 12 increments) is the uniqueness source
        // for employee/member codes - two test methods in the same run can share a cycleYear (different
        // cycleMonth keeps the payroll batch's own (month,year) constraint satisfied) but must never share
        // an employee/membership code.
        seq = CYCLE_SEQ.incrementAndGet();
        cycleYear = 2200 + (seq / 12);
        cycleMonth = 1 + (seq % 12);
        disbursementDate = LocalDate.of(cycleYear, cycleMonth, 10);

        department = departmentRepository.save(new Department("JECCSCONC", "JCIECCS Concurrency Test Dept"));
        designation = designationRepository.save(new Designation("JCIECCS Concurrency Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JECCSCONC-" + seq, "Race", "Tester", "jeccsconc" + seq + "@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-CONC-" + seq, LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        member = memberRepository.save(member);

        // Same shape as JciEccsRecoveryServiceTest: 120000/12 = 10000 principal exactly, 10% p.a. interest
        // on 120000 opening = 1000.00. Total expected this cycle: thrift 500 + interest 1000 + principal
        // 10000 = 11500.
        var created = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");
        loanId = created.id();

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JECCSCONC-" + seq, cycleMonth, cycleYear,
                cycleYear + "-" + (cycleYear + 1)));
        payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
    }

    @AfterEach
    void tearDown() {
        // jcieccs_loan_repayment.recovery_allocation_id references jcieccs_recovery_allocation - the
        // ledger rows must go first, allocations second, or the allocation delete is FK-blocked.
        // Each row's delete is individually try/caught (not the whole forEach) - a single row that fails to
        // delete must never abort cleanup of its siblings, or it leaves a PERMANENT orphan (this member/loan
        // combination is never revisited by any future test run's tearDown, since every run mints a fresh
        // member/loan - see the 2026-09-15 jcieccs_recovery id=678 residue this exact gap once produced).
        attempt(() -> { if (member != null) recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId())
                .forEach(r -> loanRepaymentRepository.findByRecovery_Id(r.getId()).forEach(rep -> attempt(() -> loanRepaymentRepository.delete(rep)))); });
        attempt(() -> { if (member != null) recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId())
                .forEach(r -> allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(r.getId()).forEach(alloc -> attempt(() -> allocationRepository.delete(alloc)))); });
        attempt(() -> { if (member != null) recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId())
                .forEach(r -> attempt(() -> recoveryRepository.delete(r))); });
        attempt(() -> { if (member != null) deleteAllThriftForMember(member.getId()); });
        // Only this test's own single collection_detail row - never the batch, never any other member's
        // row (see this class's own javadoc above on why the batch is a shared, permanent fixture).
        // findByBatch_PayrollRunIdAndEmployeeId, NOT findByBatch_IdAndEmployeeId(payrollBatch.getId(), ...)
        // - payrollBatch.getId() is the source PayrollBatch's own id, a different sequence entirely from
        // JciEccsCollectionDetail.batch (a JciEccsCollectionBatch), which is looked up by payrollRunId.
        attempt(() -> { if (payrollRunId != null && employee != null)
                collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                        .ifPresent(collectionDetailRepository::delete); });
        // A restructure/top-up test leaves a SEPARATE child loan (parent_loan_id -> loanId) with its own,
        // separately-generated schedule rows - both loans' schedules must be cleared, not just the
        // original's, or the child loan delete below is FK-blocked by its own still-existing schedule.
        attempt(() -> { if (loanId != null) loanRepository.findAll().stream()
                .filter(l -> l.getId().equals(loanId) || (l.getParentLoan() != null && l.getParentLoan().getId().equals(loanId)))
                .forEach(l -> scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(l.getId()).forEach(scheduleRepository::delete)); });
        // Child (restructured-into) loans reference the original via parent_loan_id - delete those before
        // the original, or the self-referencing FK blocks deleting the parent first.
        attempt(() -> { if (loanId != null) loanRepository.findAll().stream()
                .filter(l -> l.getParentLoan() != null && l.getParentLoan().getId().equals(loanId))
                .forEach(l -> loanRepository.deleteById(l.getId())); });
        attempt(() -> { if (loanId != null) loanRepository.deleteById(loanId); });
        // Employee deletion only runs if member deletion actually succeeded - employeeId has no real DB FK
        // from jcieccs_member (it's a plain Long column), so deleting the employee first would silently
        // leave a member row with a dangling employeeId reference forever, invisible to any FK check.
        boolean memberDeleted = attemptTracked(() -> { if (member != null) memberRepository.deleteById(member.getId()); });
        if (memberDeleted) {
            attempt(() -> { if (employee != null) employeeRepository.delete(employee); });
        }
        attempt(() -> { if (department != null) departmentRepository.delete(department); });
        attempt(() -> { if (designation != null) designationRepository.delete(designation); });
        // payrollBatch/jcieccs_collection_batch are deliberately never deleted - see class javadoc.
    }

    private void deleteAllThriftForMember(Long memberId) {
        List<JciEccsThriftTransaction> all = thriftTransactionRepository.findByMember_Id(memberId);
        all.forEach(thriftTransactionRepository::delete);
    }

    private void attempt(Runnable step) {
        attemptTracked(step);
    }

    private boolean attemptTracked(Runnable step) {
        try {
            step.run();
            return true;
        } catch (Exception ignored) {
            // best-effort cleanup - one failing step must never skip the rest
            return false;
        }
    }

    private JciEccsLoan theLoan() {
        return loanRepository.findById(loanId).orElseThrow();
    }

    /**
     * Scenario A - two identical payroll debit-confirmation callbacks for the same collection_detail line
     * arrive concurrently (Payroll's own retry-on-timeout behavior). Exactly one recovery, one allocation
     * set, one ledger posting, one balance effect must result - and neither caller may see a raw/unexpected
     * exception (the pessimistic lock added to JciEccsCollectionDetailRepository.findByBatch_IdAndEmployeeIdForUpdate
     * must serialize the race into a clean idempotent no-op for the loser, not a constraint-violation error).
     */
    @Test
    void scenarioA_duplicatePayrollCallbacks_produceExactlyOneRecoveryAndBalanceEffect() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Runnable call = () -> debitConfirmationService.confirmDebit(payrollRunId,
                    new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                            JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-A-" + seq))),
                    employee.getId());

            CompletableFuture<Object> first = raceCall(barrier, call, executor);
            CompletableFuture<Object> second = raceCall(barrier, call, executor);

            Object firstResult = first.get(20, TimeUnit.SECONDS);
            Object secondResult = second.get(20, TimeUnit.SECONDS);

            assertNeverAnUnexpectedException(firstResult);
            assertNeverAnUnexpectedException(secondResult);

            List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            assertThat(recoveries).hasSize(1);
            assertThat(recoveries.get(0).getStatus()).isEqualTo(JciEccsRecoveryStatus.POSTED);

            assertThat(allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recoveries.get(0).getId())).isNotEmpty();
            assertThat(loanRepaymentRepository.findByRecovery_Id(recoveries.get(0).getId())).hasSize(1);
            assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00"); // 120000 - 10000, exactly once
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scenario B - two concurrent cash-repayment requests carrying the SAME idempotencyKey against the
     * same loan. postCashRepayment already locks the loan (JciEccsLoanRepository.findByIdForUpdate) before
     * JciEccsRecoveryService's own idempotency check, so this proves that ordering genuinely serializes
     * the race rather than relying on the DB unique constraint alone.
     */
    @Test
    void scenarioB_duplicateCashRepaymentSameIdempotencyKey_producesExactlyOneFinancialTransaction() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            JciEccsCashRepaymentRequest request = new JciEccsCashRepaymentRequest(new BigDecimal("30000"), BigDecimal.ZERO,
                    disbursementDate.plusDays(20), "RCPT-RACE-B-" + seq, "IDEMP-RACE-B-" + seq);
            Runnable call = () -> loanService.postCashRepayment(loanId, request, employee.getId());

            CompletableFuture<Object> first = raceCall(barrier, call, executor);
            CompletableFuture<Object> second = raceCall(barrier, call, executor);

            assertNeverAnUnexpectedException(first.get(20, TimeUnit.SECONDS));
            assertNeverAnUnexpectedException(second.get(20, TimeUnit.SECONDS));

            List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            assertThat(recoveries).hasSize(1);
            assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("90000.00"); // 120000 - 30000, not 60000
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scenario C - a cash repayment that pays off the entire remaining principal races a payroll callback
     * carrying that exact same cycle's expected principal. Whichever wins, outstandingPrincipal must never
     * go negative (the posting-time cap added to JciEccsRecoveryPostingService.postLedgerAndBalance) and
     * the loser's excess (if payroll loses the race to an already-closed loan) must be flagged
     * RECONCILIATION_REQUIRED, never silently absorbed or silently dropped.
     */
    @Test
    void scenarioC_cashAndPayrollRaceTheSamePrincipal_neverGoesNegative_excessIsFlaggedNotSilent() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Pays off the entire 120000 principal in one shot via cash.
            JciEccsCashRepaymentRequest cashRequest = new JciEccsCashRepaymentRequest(new BigDecimal("120000"), new BigDecimal("1000.00"),
                    disbursementDate.plusDays(10), "RCPT-RACE-C-" + seq, "IDEMP-RACE-C-" + seq);
            // Wrapped in DeadlockRetryTemplate, exactly as JciEccsLoanController/JciEccsPayrollBatchController
            // wrap these same two calls in production - a genuine Postgres deadlock between two independent
            // lock-acquisition paths racing the same loan+collection_detail is an expected, retry-safe
            // outcome (both operations are idempotent on their own key), not a raw error a caller should see.
            Runnable cashCall = () -> deadlockRetryTemplate.execute(() -> loanService.postCashRepayment(loanId, cashRequest, employee.getId()));
            Runnable payrollCall = () -> deadlockRetryTemplate.execute(() -> debitConfirmationService.confirmDebit(payrollRunId,
                    new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                            JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-C-" + seq))),
                    employee.getId()));

            CompletableFuture<Object> cash = raceCall(barrier, cashCall, executor);
            CompletableFuture<Object> payroll = raceCall(barrier, payrollCall, executor);

            assertNeverAnUnexpectedException(cash.get(20, TimeUnit.SECONDS));
            assertNeverAnUnexpectedException(payroll.get(20, TimeUnit.SECONDS));

            JciEccsLoan finalLoan = theLoan();
            assertThat(finalLoan.getOutstandingPrincipal().signum()).isGreaterThanOrEqualTo(0); // never negative, whichever order won
            assertThat(finalLoan.getStatus()).isEqualTo(JciEccsLoanStatus.CLOSED);

            List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            var detailAfter = collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId()).orElseThrow();
            if (recoveries.size() == 1) {
                // Cash won outright and its own flagLockedSnapshotIfAny correctly flipped this still-PENDING_DEBIT
                // line to RECONCILIATION_REQUIRED before payroll's (possibly retried) attempt ever reached it -
                // JciEccsDebitConfirmationService.processLine's own PENDING_DEBIT gate then correctly skips a
                // line that's no longer PENDING_DEBIT, rather than posting a payroll recovery the operator
                // still needs to manually reconcile. This is the CASH_RECOVERY_AFTER_SNAPSHOT_LOCK contract
                // working as designed, not a dropped payroll recovery.
                assertThat(recoveries.get(0).getSource()).isEqualTo(JciEccsRecoverySource.CASH);
                assertThat(detailAfter.getDebitStatus()).isEqualTo(in.gov.jci.hrms.entity.JciEccsDebitStatus.RECONCILIATION_REQUIRED);
                assertThat(detailAfter.getReconciliationReason()).isEqualTo("CASH_RECOVERY_AFTER_SNAPSHOT_LOCK");
            } else {
                // Payroll's line was still PENDING_DEBIT when it ran (it won the race, or its retry ran before
                // cash's flag landed) - both a CASH and a PAYROLL recovery genuinely exist.
                assertThat(recoveries).hasSize(2);
                boolean anyReconciliationRequired = recoveries.stream().anyMatch(r -> r.getStatus() == JciEccsRecoveryStatus.RECONCILIATION_REQUIRED);
                boolean bothFullyPosted = recoveries.stream().allMatch(r -> r.getStatus() == JciEccsRecoveryStatus.POSTED);
                // Exactly one of these two outcomes is valid depending on which recovery won the lock race -
                // either both legitimately fit (cash first, remaining payroll principal was zero so nothing
                // to over-recover), or the loser's full amount was already-collected-but-unneeded principal,
                // which must be flagged rather than silently pushing the balance negative.
                assertThat(anyReconciliationRequired || bothFullyPosted).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scenario D (part 1) - two concurrent requests to reverse the SAME recovery. The pessimistic lock
     * added to JciEccsRecoveryRepository.findByIdForUpdate must serialize this into "one reversal succeeds,
     * the other fails cleanly with a business exception" - never a double reversal, never a raw constraint
     * violation.
     */
    @Test
    void scenarioD1_duplicateReversalOfTheSameRecovery_isAppliedExactlyOnce() throws Exception {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-D1-" + seq))),
                employee.getId());
        JciEccsRecovery original = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).get(0);
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // SEC-004 (docs/security/SEC_001_002_REMEDIATION.md pattern): maker != checker.
            Runnable call = () -> recoveryService.reverseRecovery(original.getId(), "Race reversal", employee.getId() + 1_000_000L);
            CompletableFuture<Object> first = raceCall(barrier, call, executor);
            CompletableFuture<Object> second = raceCall(barrier, call, executor);

            Object firstResult = first.get(20, TimeUnit.SECONDS);
            Object secondResult = second.get(20, TimeUnit.SECONDS);

            // Both may succeed (the idempotency-key replay returns the same reversal to both callers) or
            // exactly one may throw BusinessRuleViolationException ("already REVERSED") - either way, never
            // an unexpected exception type, and never two reversal rows.
            if (firstResult instanceof Exception e) {
                assertThat(e).isInstanceOf(BusinessRuleViolationException.class);
            }
            if (secondResult instanceof Exception e) {
                assertThat(e).isInstanceOf(BusinessRuleViolationException.class);
            }

            List<JciEccsRecovery> allForMember = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            long reversalCount = allForMember.stream().filter(r -> r.getSource() == JciEccsRecoverySource.REVERSAL).count();
            assertThat(reversalCount).isEqualTo(1);
            assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("120000.00"); // restored exactly once
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scenario D (part 2) - a reversal of an existing recovery races a brand-new cash repayment against
     * the same loan. Both funnel through the same per-loan pessimistic lock inside
     * JciEccsRecoveryPostingService, so the result must be deterministic (one serialization order or the
     * other) and never a lost update - the final outstanding must equal exactly one of the two valid
     * serializations, never something in between (which would indicate one write clobbered the other).
     */
    @Test
    void scenarioD2_reversalRacesANewRepayment_neverLosesAnUpdate() throws Exception {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-D2-" + seq))),
                employee.getId());
        JciEccsRecovery original = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).get(0);
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // SEC-004 (docs/security/SEC_001_002_REMEDIATION.md pattern): maker != checker.
            Runnable reversalCall = () -> recoveryService.reverseRecovery(original.getId(), "Race vs new cash", employee.getId() + 1_000_000L);
            JciEccsCashRepaymentRequest cashRequest = new JciEccsCashRepaymentRequest(new BigDecimal("5000"), BigDecimal.ZERO,
                    disbursementDate.plusDays(12), "RCPT-RACE-D2-" + seq, "IDEMP-RACE-D2-" + seq);
            Runnable cashCall = () -> loanService.postCashRepayment(loanId, cashRequest, employee.getId());

            CompletableFuture<Object> reversal = raceCall(barrier, reversalCall, executor);
            CompletableFuture<Object> cash = raceCall(barrier, cashCall, executor);

            assertNeverAnUnexpectedException(reversal.get(20, TimeUnit.SECONDS));
            assertNeverAnUnexpectedException(cash.get(20, TimeUnit.SECONDS));

            // Reversal restores +10000 (110000 -> 120000); the new cash repayment subtracts 5000 - applied
            // in either order, the deterministic final result is the same: 115000.00. A lost update would
            // instead leave either 110000.00 (cash's write clobbered by a stale reversal write) or
            // 120000.00 (reversal's write clobbered by a stale cash write).
            assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("115000.00");

            List<JciEccsRecovery> allForMember = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            assertThat(allForMember.stream().filter(r -> r.getSource() == JciEccsRecoverySource.REVERSAL).count()).isEqualTo(1);
            assertThat(allForMember.stream().filter(r -> r.getSource() == JciEccsRecoverySource.CASH).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scenario E - two concurrent restructure requests against the same TE loan. lockActiveLoanOrThrow's
     * existing pessimistic lock must serialize this: exactly one restructure succeeds (old loan ->
     * RESTRUCTURED, one new loan created), the other cleanly fails because the loan is no longer ACTIVE by
     * the time it acquires the lock - never two conflicting new-loan schedule versions.
     */
    @Test
    void scenarioE_concurrentRestructureOfTheSameLoan_exactlyOneSucceeds() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            JciEccsRestructureRequest request = new JciEccsRestructureRequest(18, disbursementDate.plusMonths(1));
            Runnable call = () -> restructureService.restructure(loanId, request, employee.getId());

            CompletableFuture<Object> first = raceCall(barrier, call, executor);
            CompletableFuture<Object> second = raceCall(barrier, call, executor);

            Object firstResult = first.get(20, TimeUnit.SECONDS);
            Object secondResult = second.get(20, TimeUnit.SECONDS);

            long successes = List.of(firstResult, secondResult).stream().filter(r -> !(r instanceof Exception)).count();
            long failures = List.of(firstResult, secondResult).stream().filter(r -> r instanceof Exception).count();
            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);
            Object failure = firstResult instanceof Exception ? firstResult : secondResult;
            assertThat((Exception) failure).isInstanceOf(BusinessRuleViolationException.class);

            assertThat(theLoan().getStatus()).isEqualTo(JciEccsLoanStatus.RESTRUCTURED);
            List<JciEccsLoan> children = loanRepository.findAll().stream()
                    .filter(l -> l.getParentLoan() != null && l.getParentLoan().getId().equals(loanId)).toList();
            assertThat(children).hasSize(1); // exactly one new loan, not two conflicting versions
        } finally {
            executor.shutdownNow();
        }
    }

    private CompletableFuture<Object> raceCall(CyclicBarrier barrier, Runnable call, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                call.run();
                return "OK";
            } catch (Exception e) {
                return e;
            }
        }, executor);
    }

    private void assertNeverAnUnexpectedException(Object result) {
        if (result instanceof Exception e) {
            assertThat(e).as("no raw/unexpected exception should leak out of a serialized race")
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
