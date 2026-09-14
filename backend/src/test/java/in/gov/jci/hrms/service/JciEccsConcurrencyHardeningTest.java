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

    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2091, 5, 10);

    private Department department;
    private Designation designation;
    private Employee employee;
    private JciEccsMember member;
    private PayrollBatch payrollBatch;
    private String payrollRunId;
    private Long loanId;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("JECCSCONC", "JCIECCS Concurrency Test Dept"));
        designation = designationRepository.save(new Designation("JCIECCS Concurrency Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JECCSCONC-1", "Race", "Tester", "jeccsconc1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-CONC-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        member = memberRepository.save(member);

        // Same shape as JciEccsRecoveryServiceTest: 120000/12 = 10000 principal exactly, 10% p.a. interest
        // on 120000 opening = 1000.00. Total expected this cycle: thrift 500 + interest 1000 + principal
        // 10000 = 11500.
        var created = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, DISBURSEMENT_DATE, DISBURSEMENT_DATE, DISBURSEMENT_DATE), employee.getId(), "tester");
        loanId = created.id();

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JECCSCONC-2091-05", 5, 2091, "2090-2091"));
        payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
    }

    @AfterEach
    void tearDown() {
        attempt(() -> { if (loanId != null) loanRepaymentRepository.findByRecovery_Id(-1L); }); // no-op warm-up, keeps ordering explicit
        attempt(() -> { if (member != null) recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId())
                .forEach(r -> { allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(r.getId()).forEach(allocationRepository::delete);
                                loanRepaymentRepository.findByRecovery_Id(r.getId()).forEach(loanRepaymentRepository::delete); }); });
        attempt(() -> { if (member != null) recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).forEach(recoveryRepository::delete); });
        attempt(() -> { if (member != null) thriftTransactionRepository.findTopByMember_IdOrderByIdDesc(member.getId())
                .ifPresent(t -> deleteAllThriftForMember(member.getId())); });
        attempt(() -> { if (payrollBatch != null) collectionDetailRepository.findByBatch_IdOrderByEmployeeIdAsc(payrollBatch.getId()).forEach(collectionDetailRepository::delete); });
        attempt(() -> { if (payrollBatch != null) payrollBatchRepository.deleteById(payrollBatch.getId()); });
        attempt(() -> { if (loanId != null) scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loanId).forEach(scheduleRepository::delete); });
        attempt(() -> loanRepository.findAll().stream().filter(l -> l.getMember() != null && l.getMember().getId().equals(member.getId()))
                .forEach(l -> loanRepository.deleteById(l.getId())));
        attempt(() -> { if (member != null) memberRepository.deleteById(member.getId()); });
        attempt(() -> { if (employee != null) employeeRepository.delete(employee); });
        attempt(() -> { if (department != null) departmentRepository.delete(department); });
        attempt(() -> { if (designation != null) designationRepository.delete(designation); });
    }

    private void deleteAllThriftForMember(Long memberId) {
        List<JciEccsThriftTransaction> all = thriftTransactionRepository.findByMember_Id(memberId);
        all.forEach(thriftTransactionRepository::delete);
    }

    private void attempt(Runnable step) {
        try {
            step.run();
        } catch (Exception ignored) {
            // best-effort cleanup - one failing step must never skip the rest
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
                            JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-A"))),
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
                    LocalDate.of(2091, 6, 1), "RCPT-RACE-B", "IDEMP-RACE-B");
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
                    LocalDate.of(2091, 5, 20), "RCPT-RACE-C", "IDEMP-RACE-C");
            Runnable cashCall = () -> loanService.postCashRepayment(loanId, cashRequest, employee.getId());
            Runnable payrollCall = () -> debitConfirmationService.confirmDebit(payrollRunId,
                    new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                            JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-C"))),
                    employee.getId());

            CompletableFuture<Object> cash = raceCall(barrier, cashCall, executor);
            CompletableFuture<Object> payroll = raceCall(barrier, payrollCall, executor);

            assertNeverAnUnexpectedException(cash.get(20, TimeUnit.SECONDS));
            assertNeverAnUnexpectedException(payroll.get(20, TimeUnit.SECONDS));

            JciEccsLoan finalLoan = theLoan();
            assertThat(finalLoan.getOutstandingPrincipal().signum()).isGreaterThanOrEqualTo(0); // never negative, whichever order won
            assertThat(finalLoan.getStatus()).isEqualTo(JciEccsLoanStatus.CLOSED);

            List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
            assertThat(recoveries).hasSize(2); // one CASH, one PAYROLL - both real, neither silently dropped
            boolean anyReconciliationRequired = recoveries.stream().anyMatch(r -> r.getStatus() == JciEccsRecoveryStatus.RECONCILIATION_REQUIRED);
            boolean bothFullyPosted = recoveries.stream().allMatch(r -> r.getStatus() == JciEccsRecoveryStatus.POSTED);
            // Exactly one of these two outcomes is valid depending on which recovery won the lock race -
            // either both legitimately fit (cash first, small remaining payroll principal was zero so
            // nothing to over-recover), or the loser's full amount was already-collected-but-unneeded
            // principal, which must be flagged rather than silently pushing the balance negative.
            assertThat(anyReconciliationRequired || bothFullyPosted).isTrue();
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
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-D1"))),
                employee.getId());
        JciEccsRecovery original = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).get(0);
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Runnable call = () -> recoveryService.reverseRecovery(original.getId(), "Race reversal", employee.getId());
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
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-RACE-D2"))),
                employee.getId());
        JciEccsRecovery original = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).get(0);
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Runnable reversalCall = () -> recoveryService.reverseRecovery(original.getId(), "Race vs new cash", employee.getId());
            JciEccsCashRepaymentRequest cashRequest = new JciEccsCashRepaymentRequest(new BigDecimal("5000"), BigDecimal.ZERO,
                    LocalDate.of(2091, 5, 22), "RCPT-RACE-D2", "IDEMP-RACE-D2");
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
            JciEccsRestructureRequest request = new JciEccsRestructureRequest(18, LocalDate.of(2091, 6, 1));
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
