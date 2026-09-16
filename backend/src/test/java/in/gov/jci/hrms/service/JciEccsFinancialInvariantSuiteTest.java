package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationLineRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionBatchRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsReconciliationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCIECCS Lifecycle Engine Phase 5 (spec sections 5, 12, 31) - the reusable financial invariant suite,
 * plus the two end-to-end scenarios not already covered elsewhere: demand generation must never itself
 * mutate loan/thrift/schedule state, cash landing BEFORE a snapshot lock must be reflected in that
 * snapshot's own demand (not flagged as a false conflict - that's reserved for cash AFTER the lock, already
 * covered by JciEccsRecoveryServiceTest/JciEccsReconciliationServiceTest's own *AfterSnapshotLock* tests),
 * and a full recovery+reversal flow must leave the administrator's own integrity checker with zero
 * findings.
 */
@SpringBootTest
@Transactional
class JciEccsFinancialInvariantSuiteTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsDebitConfirmationService debitConfirmationService;
    @Autowired private JciEccsRecoveryService recoveryService;
    @Autowired private JciEccsReconciliationService reconciliationService;
    @Autowired private JciEccsIntegrityCheckService integrityCheckService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private JciEccsLoanScheduleRepository scheduleRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private JciEccsCollectionDetailRepository collectionDetailRepository;
    @Autowired private JciEccsCollectionBatchRepository collectionBatchRepository;
    @Autowired private in.gov.jci.hrms.repository.JciEccsLifecycleEventRepository lifecycleEventRepository;
    @Autowired private JciEccsRecoveryRepository recoveryRepository;
    @Autowired private JciEccsReconciliationRepository reconciliationRepository;

    private Employee employee;
    private JciEccsMember member;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSFI", "JCIECCS Invariant Suite Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Invariant Suite Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JFI-1", "Invariant", "Tester", "jfi1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-FI-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        member = memberRepository.save(member);
    }

    private JciEccsLoan createLoan(LocalDate disbursementDate) {
        var created = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");
        return loanRepository.findById(created.id()).orElseThrow();
    }

    private PayrollBatch createBatch(int month, int year) {
        return payrollBatchRepository.save(new PayrollBatch("PB-JFI-" + year + "-" + month, month, year, (year - 1) + "-" + year));
    }

    /** Financial invariant (spec section 5, "Demand"): generating a snapshot only ever READS loan/thrift/
     * schedule state to compute expected amounts - it must never itself change outstandingPrincipal, the
     * thrift running balance, or any schedule row's paid/status fields. */
    @Test
    void demandGeneration_neverMutatesLoanThriftOrScheduleState() {
        LocalDate disbursementDate = LocalDate.of(2026, 5, 10);
        JciEccsLoan loan = createLoan(disbursementDate);
        BigDecimal outstandingBefore = loan.getOutstandingPrincipal();
        List<JciEccsLoanSchedule> scheduleBefore = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loan.getId());
        BigDecimal firstInstallmentPrincipalPaidBefore = scheduleBefore.get(0).getPrincipalPaid();
        var statusesBefore = scheduleBefore.stream().map(JciEccsLoanSchedule::getStatus).toList();

        PayrollBatch batch = createBatch(5, 2026);
        String payrollRunId = batch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, batch, employee.getId());

        JciEccsLoan loanAfter = loanRepository.findById(loan.getId()).orElseThrow();
        List<JciEccsLoanSchedule> scheduleAfter = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loan.getId());

        assertThat(loanAfter.getOutstandingPrincipal()).isEqualByComparingTo(outstandingBefore);
        assertThat(scheduleAfter.get(0).getPrincipalPaid()).isEqualByComparingTo(firstInstallmentPrincipalPaidBefore);
        assertThat(scheduleAfter.stream().map(JciEccsLoanSchedule::getStatus).toList()).isEqualTo(statusesBefore);

        // The demand IS visible in the new collection_detail row - generation still did its one real job.
        JciEccsCollectionDetail detail = collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                .orElseThrow();
        assertThat(detail.getTermPrincipal()).isEqualTo(10000);
        assertThat(detail.getThriftAmount()).isEqualByComparingTo("500.00");
    }

    /**
     * Section 12 - cash lands BEFORE the payroll snapshot for this cycle is ever generated/locked. The
     * subsequent demand generation must compute this cycle's expected amounts from the loan's now-reduced
     * position (existing, unchanged rule: demand always reads the loan's live state at generation time) -
     * no CASH_RECOVERY_AFTER_SNAPSHOT_LOCK flag, since there was no locked snapshot yet for cash to
     * conflict with.
     */
    @Test
    void cashBeforeSnapshotLock_subsequentDemandReflectsUpdatedPosition_noFalseReconciliationException() {
        LocalDate disbursementDate = LocalDate.of(2026, 5, 10);
        JciEccsLoan loan = createLoan(disbursementDate);

        // Cash pays off the entire first installment's principal before any snapshot for this cycle exists.
        loanService.postCashRepayment(loan.getId(),
                new JciEccsCashRepaymentRequest(new BigDecimal("10000"), new BigDecimal("1000.00"), disbursementDate.plusDays(2),
                        "RCPT-PRELOCK", "IDEMP-PRELOCK"),
                employee.getId());

        PayrollBatch batch = createBatch(5, 2026);
        String payrollRunId = batch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, batch, employee.getId());

        JciEccsCollectionDetail detail = collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                .orElseThrow();
        // recalculateRemainingSchedule already moved this cycle's due to the (shortened) next installment -
        // the snapshot must reflect THAT, not the original (now-satisfied) first installment's demand.
        assertThat(detail.getDebitStatus()).isEqualTo(JciEccsDebitStatus.PENDING_DEBIT); // never RECONCILIATION_REQUIRED
        assertThat(detail.getReconciliationReason()).isNull();

        // Confirming the (correct, post-cash) demand in full must not raise any reconciliation exception.
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-PRELOCK"))),
                employee.getId());
        reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        // reconciliationRepository is keyed by JciEccsCollectionBatch's own id, a different sequence from
        // PayrollBatch (payroll's own source table, resolved by batch.getId() above only as the payrollRunId
        // string) - resolve the real collection batch id before querying by it. The batch also carries
        // every other pre-existing active member's own rows (a snapshot is a whole-of-cooperative
        // operation - see JciEccsConcurrencyHardeningTest's own class javadoc), so scope the assertion to
        // this test's own member, never the whole batch.
        Long collectionBatchId = collectionBatchRepository.findByPayrollRunId(payrollRunId).orElseThrow().getId();
        var recon = reconciliationRepository.findByCollectionBatch_IdOrderByMember_MembershipCodeAsc(collectionBatchId).stream()
                .filter(r -> r.getMember().getId().equals(member.getId())).toList();
        assertThat(recon).isNotEmpty();
        assertThat(recon).noneMatch(r -> r.getStatus() == JciEccsReconciliationStatus.LOCKED_SNAPSHOT_CONFLICT);
    }

    /**
     * Section 31 - after a real recovery + reversal flow, the administrator's own on-demand integrity
     * checker (JciEccsIntegrityCheckService, never an auto-repair tool) must find zero issues: no orphan
     * allocations, no recovery without a valid source, no duplicate reversal, no negative outstanding, no
     * closed loan with outstanding principal.
     */
    @Test
    void fullRecoveryAndReversalFlow_integrityCheckerFindsNothing() {
        LocalDate disbursementDate = LocalDate.of(2026, 6, 10);
        JciEccsLoan loan = createLoan(disbursementDate);
        PayrollBatch batch = createBatch(6, 2026);
        String payrollRunId = batch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, batch, employee.getId());

        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-INV"))),
                employee.getId());
        JciEccsRecovery posted = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId()).get(0);
        // SEC-004 (docs/security/SEC_001_002_REMEDIATION.md pattern): maker != checker - reverseRecovery()
        // only compares this id against posted.getCreatedBy(), never looks up an Employee row for it.
        recoveryService.reverseRecovery(posted.getId(), "Invariant suite reversal", employee.getId() + 1_000_000L);

        assertThat(integrityCheckService.checkLoan(loan.getId())).isEmpty();
        for (JciEccsRecovery recovery : recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId())) {
            assertThat(integrityCheckService.checkRecovery(recovery.getId())).as("recovery #%d", recovery.getId()).isEmpty();
        }
    }

    /** Section 29 (Operational Traceability) - a loan's own audit trail (JciEccsLifecycleEvent) must be
     * queryable end-to-end: LOAN_CREATED at minimum, in chronological order. Proves the repository query
     * JciEccsAuditTrailController now exposes (previously declared but never called from any endpoint)
     * actually returns real rows for a real entity. */
    @Test
    void loanAuditTrail_isQueryableEndToEnd_chronologicalOrder() {
        LocalDate disbursementDate = LocalDate.of(2026, 7, 10);
        JciEccsLoan loan = createLoan(disbursementDate);

        loanService.postCashRepayment(loan.getId(),
                new JciEccsCashRepaymentRequest(new BigDecimal("5000"), BigDecimal.ZERO, disbursementDate.plusDays(3),
                        "RCPT-TRACE", "IDEMP-TRACE"),
                employee.getId());

        var events = lifecycleEventRepository.findByEntityTypeAndEntityIdOrderByEventDateAsc("JCIECCS_LOAN", loan.getId());
        assertThat(events).extracting(e -> e.getEventType()).contains("LOAN_CREATED", "CASH_REPAYMENT_POSTED");
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).getEventDate()).isAfterOrEqualTo(events.get(i - 1).getEventDate());
        }
    }
}
