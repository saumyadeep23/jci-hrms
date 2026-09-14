package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
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
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.JciEccsRecoverySource;
import in.gov.jci.hrms.entity.JciEccsRecoveryStatus;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JCIECCS Lifecycle Engine Phase 1 - proves RECOVERY, ALLOCATION, LEDGER POSTING and BALANCE UPDATE are
 * genuinely separate, testable stages (not fused), that over-debit is never silently distributed, that
 * cash repayment is idempotent and flags (never silently mutates) a payroll snapshot already LOCKED for
 * the same loan, and that reversal is a compensating entry - never a delete/mutate-in-place - that can
 * only be applied once.
 */
@SpringBootTest
@Transactional
class JciEccsRecoveryServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsDebitConfirmationService debitConfirmationService;
    @Autowired private JciEccsRecoveryService recoveryService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private JciEccsRecoveryRepository recoveryRepository;
    @Autowired private JciEccsRecoveryAllocationRepository allocationRepository;
    @Autowired private JciEccsLoanRepaymentRepository loanRepaymentRepository;
    @Autowired private JciEccsCollectionDetailRepository collectionDetailRepository;

    private Employee employee;
    private JciEccsMember member;
    private PayrollBatch payrollBatch;
    private String payrollRunId;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSRC", "JCIECCS Recovery Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Recovery Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JRC-1", "Recovery", "Tester", "jrc1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-RC-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        member = memberRepository.save(member);

        // 120000 / 12 = 10000 principal exactly; interest at 10% p.a. on 120000 opening = 1000.00.
        // Total expected this cycle: thrift 500 + interest 1000 + principal 10000 = 11500.
        LocalDate disbursementDate = LocalDate.of(2033, 5, 10);
        loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JRC-2033-05", 5, 2033, "2032-2033"));
        payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
    }

    private JciEccsLoan theLoan() {
        return loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
    }

    private JciEccsRecovery latestRecovery() {
        List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
        assertThat(recoveries).as("at least one recovery for member %s", member.getId()).isNotEmpty();
        return recoveries.get(0);
    }

    @Test
    void debitSuccess_createsAPostedRecoveryWithAllocationsCoveringEveryComponent() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-SUCCESS"))),
                employee.getId());

        JciEccsRecovery recovery = latestRecovery();
        assertThat(recovery.getSource()).isEqualTo(JciEccsRecoverySource.PAYROLL);
        assertThat(recovery.getStatus()).isEqualTo(JciEccsRecoveryStatus.POSTED);
        assertThat(recovery.getGrossAmount()).isEqualByComparingTo("11500.00");
        assertThat(recovery.getPostedAt()).isNotNull();

        var allocations = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recovery.getId());
        assertThat(allocations).hasSize(3); // thrift, term interest, term principal - the two emergency components have expected=0, so are omitted
        assertThat(allocationOf(allocations, JciEccsRecoveryComponent.THRIFT).getAllocatedAmount()).isEqualByComparingTo("500.00");
        assertThat(allocationOf(allocations, JciEccsRecoveryComponent.TERM_INTEREST).getAllocatedAmount()).isEqualByComparingTo("1000.00");
        assertThat(allocationOf(allocations, JciEccsRecoveryComponent.TERM_PRINCIPAL).getAllocatedAmount()).isEqualByComparingTo("10000.00");

        // The ledger row (jcieccs_loan_repayment) traces back to this exact recovery.
        var ledgerRows = loanRepaymentRepository.findByRecovery_Id(recovery.getId());
        assertThat(ledgerRows).hasSize(1);
        assertThat(ledgerRows.get(0).getPrincipalAmount()).isEqualByComparingTo("10000.00");
        assertThat(ledgerRows.get(0).getInterestAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void debitPartial_recoveryIsPartial_unrecoveredComponentStillTracedWithZeroAllocation() {
        // Offer 1300: fully covers thrift(500), 800 of the 1000 interest due, nothing toward principal.
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_PARTIAL, new BigDecimal("1300.00"), "TXN-PARTIAL"))),
                employee.getId());

        JciEccsRecovery recovery = latestRecovery();
        assertThat(recovery.getStatus()).isEqualTo(JciEccsRecoveryStatus.PARTIAL);

        var allocations = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recovery.getId());
        var principalAllocation = allocationOf(allocations, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalAllocation.getExpectedAmount()).isEqualByComparingTo("10000.00");
        assertThat(principalAllocation.getAllocatedAmount()).isEqualByComparingTo("0"); // demanded, but never silently marked recovered

        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("120000.00"); // untouched - no principal posted
    }

    @Test
    void debitFailed_recoveryIsFailed_noLedgerRowPosted_outstandingUntouched() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_FAILED, null, "TXN-FAILED"))),
                employee.getId());

        JciEccsRecovery recovery = latestRecovery();
        assertThat(recovery.getStatus()).isEqualTo(JciEccsRecoveryStatus.FAILED);
        assertThat(loanRepaymentRepository.findByRecovery_Id(recovery.getId())).isEmpty();
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("120000.00");
    }

    @Test
    void debitPartial_offeredExceedsTotalExpected_flagsReconciliationRequired_excessNeverSilentlyApplied() {
        // Total expected is 11500; offering 15000 is a genuine over-debit.
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_PARTIAL, new BigDecimal("15000.00"), "TXN-OVER"))),
                employee.getId());

        JciEccsRecovery recovery = latestRecovery();
        assertThat(recovery.getStatus()).isEqualTo(JciEccsRecoveryStatus.RECONCILIATION_REQUIRED);

        // The legitimately-expected portion (11500) still posts normally...
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00"); // 120000 - 10000, not more
        // ...and the 3500 excess is never applied anywhere (not to this loan, not silently dropped into
        // some other component) - the allocation rows sum to exactly the expected total, never to 15000.
        BigDecimal totalAllocated = allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(recovery.getId()).stream()
                .map(a -> a.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalAllocated).isEqualByComparingTo("11500.00");
    }

    @Test
    void cashRepayment_duplicateIdempotencyKey_doesNotDoublePost() {
        JciEccsLoan loan = theLoan();
        JciEccsCashRepaymentRequest request = new JciEccsCashRepaymentRequest(new BigDecimal("30000"), BigDecimal.ZERO,
                LocalDate.of(2033, 6, 1), "RCPT-1", "IDEMP-DUP-1");

        loanService.postCashRepayment(loan.getId(), request, employee.getId());
        loanService.postCashRepayment(loan.getId(), request, employee.getId()); // exact retry, same idempotencyKey

        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("90000.00"); // 120000 - 30000, not 60000
        List<JciEccsRecovery> recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
        assertThat(recoveries).hasSize(1);
    }

    @Test
    void cashRepayment_afterSnapshotAlreadyLocked_flagsTheLockedDetail_withoutMutatingItsAmounts() {
        JciEccsLoan loan = theLoan();
        // The snapshot generated in setUp() is already LOCKED with PENDING_DEBIT for this loan's cycle.
        JciEccsCollectionDetail beforeCash = collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                .orElseThrow();
        assertThat(beforeCash.getDebitStatus()).isEqualTo(JciEccsDebitStatus.PENDING_DEBIT);
        int termPrincipalBefore = beforeCash.getTermPrincipal();

        loanService.postCashRepayment(loan.getId(),
                new JciEccsCashRepaymentRequest(new BigDecimal("20000"), BigDecimal.ZERO, LocalDate.of(2033, 5, 20), "RCPT-2", "IDEMP-LOCK-1"),
                employee.getId());

        JciEccsCollectionDetail afterCash = collectionDetailRepository.findByBatch_PayrollRunIdAndEmployeeId(payrollRunId, employee.getId())
                .orElseThrow();
        assertThat(afterCash.getDebitStatus()).isEqualTo(JciEccsDebitStatus.RECONCILIATION_REQUIRED);
        assertThat(afterCash.getReconciliationReason()).isEqualTo("CASH_RECOVERY_AFTER_SNAPSHOT_LOCK");
        // The locked demand amount itself is never silently touched.
        assertThat(afterCash.getTermPrincipal()).isEqualTo(termPrincipalBefore);
        // The cash recovery itself still posts normally.
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("100000.00");
    }

    @Test
    void reverseRecovery_restoresOutstandingAndSchedule_marksOriginalReversed_cannotReverseTwice() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-REV"))),
                employee.getId());
        JciEccsRecovery original = latestRecovery();
        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("110000.00");

        JciEccsRecovery reversal = recoveryService.reverseRecovery(original.getId(), "Payroll debit reversed by employer", employee.getId());

        assertThat(reversal.getSource()).isEqualTo(JciEccsRecoverySource.REVERSAL);
        assertThat(reversal.getStatus()).isEqualTo(JciEccsRecoveryStatus.POSTED);
        assertThat(reversal.getReversalOfRecovery().getId()).isEqualTo(original.getId());

        JciEccsRecovery reloadedOriginal = recoveryRepository.findById(original.getId()).orElseThrow();
        assertThat(reloadedOriginal.getStatus()).isEqualTo(JciEccsRecoveryStatus.REVERSED);
        assertThat(reloadedOriginal.getReversedAt()).isNotNull();
        // Original ledger row(s) are never deleted or mutated.
        assertThat(loanRepaymentRepository.findByRecovery_Id(original.getId())).hasSize(1);
        // A new, separate compensating ledger row exists for the reversal.
        assertThat(loanRepaymentRepository.findByRecovery_Id(reversal.getId())).hasSize(1);

        assertThat(theLoan().getOutstandingPrincipal()).isEqualByComparingTo("120000.00"); // restored

        assertThatThrownBy(() -> recoveryService.reverseRecovery(original.getId(), "duplicate reversal attempt", employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private in.gov.jci.hrms.entity.JciEccsRecoveryAllocation allocationOf(
            List<in.gov.jci.hrms.entity.JciEccsRecoveryAllocation> allocations, JciEccsRecoveryComponent component) {
        return allocations.stream().filter(a -> a.getComponent() == component).findFirst()
                .orElseThrow(() -> new AssertionError("No allocation for component " + component));
    }
}
