package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationLineRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsReconciliation;
import in.gov.jci.hrms.entity.JciEccsReconciliationReasonCode;
import in.gov.jci.hrms.entity.JciEccsReconciliationResolutionAction;
import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsReconciliationRepository;
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
 * JCIECCS Lifecycle Engine Phase 2 - three-way reconciliation (DEMAND vs ACTUAL RECOVERY vs LEDGER
 * POSTING), loan-level balance reconciliation, integrity checks and exception resolution. Never asserts
 * that the service mutates loan/schedule/ledger state - only that it correctly DETECTS and RECORDS.
 */
@SpringBootTest
@Transactional
class JciEccsReconciliationServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsDebitConfirmationService debitConfirmationService;
    @Autowired private JciEccsReconciliationService reconciliationService;
    @Autowired private JciEccsIntegrityCheckService integrityCheckService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private JciEccsReconciliationRepository reconciliationRepository;
    @Autowired private JciEccsRecoveryService recoveryService;
    @Autowired private in.gov.jci.hrms.repository.JciEccsRecoveryRepository recoveryRepository;

    private Employee employee;
    private JciEccsMember member;
    private PayrollBatch payrollBatch;
    private String payrollRunId;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSRE", "JCIECCS Reconciliation Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Reconciliation Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JRE-1", "Recon", "Tester", "jre1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-RE-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        member = memberRepository.save(member);

        // 120000 / 12 = 10000 principal exactly; interest 10% p.a. on 120000 = 1000.00.
        // Total expected: thrift 500 + interest 1000 + principal 10000 = 11500.
        LocalDate disbursementDate = LocalDate.of(2034, 3, 10);
        loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JRE-2034-03", 3, 2034, "2033-2034"));
        payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
    }

    private JciEccsLoan theLoan() {
        return loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
    }

    private JciEccsReconciliation rowFor(JciEccsReconciliationService.ReconciliationSummary ignored, JciEccsRecoveryComponent component) {
        List<JciEccsReconciliation> rows = reconciliationRepository.findByMember_IdOrderByDetectedAtDesc(member.getId());
        return rows.stream().filter(r -> r.getComponent() == component).findFirst()
                .orElseThrow(() -> new AssertionError("No reconciliation row for " + component));
    }

    @Test
    void fullDebit_reconciliationIsMatchedForEveryComponent() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-MATCH"))),
                employee.getId());

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        assertThat(summary.countsByStatus().getOrDefault(JciEccsReconciliationStatus.MATCHED, 0)).isEqualTo(3); // thrift, interest, principal
        assertThat(summary.countsByStatus().getOrDefault(JciEccsReconciliationStatus.PARTIAL, 0)).isZero();

        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.MATCHED);
        assertThat(principalRow.getExpectedAmount()).isEqualByComparingTo("10000.00");
        assertThat(principalRow.getActualAmount()).isEqualByComparingTo("10000.00");
        assertThat(principalRow.getPostedAmount()).isEqualByComparingTo("10000.00");
        assertThat(principalRow.getVarianceAmount()).isEqualByComparingTo("0");
    }

    @Test
    void partialDebit_reconciliationIsPartialForTheUnrecoveredComponent() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_PARTIAL, new BigDecimal("1300.00"), "TXN-PARTIAL"))),
                employee.getId());

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.NOT_RECOVERED); // 0 of 10000 recovered
        assertThat(principalRow.getReasonCode()).isEqualTo(JciEccsReconciliationReasonCode.FAILED_PAYROLL_DEBIT);

        var interestRow = rowFor(summary, JciEccsRecoveryComponent.TERM_INTEREST);
        assertThat(interestRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.PARTIAL); // 800 of 1000
        assertThat(interestRow.getReasonCode()).isEqualTo(JciEccsReconciliationReasonCode.PARTIAL_PAYROLL_DEBIT);
        assertThat(interestRow.getVarianceAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void failedDebit_reconciliationIsNotRecoveredForEveryComponent() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_FAILED, null, "TXN-FAILED"))),
                employee.getId());

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        assertThat(summary.countsByStatus().getOrDefault(JciEccsReconciliationStatus.NOT_RECOVERED, 0)).isEqualTo(3);
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getPostedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void overDebit_reconciliationFlagsOverRecovered() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_PARTIAL, new BigDecimal("15000.00"), "TXN-OVER"))),
                employee.getId());

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.OVER_RECOVERED);
        assertThat(principalRow.getReasonCode()).isEqualTo(JciEccsReconciliationReasonCode.OVER_DEBIT);
    }

    @Test
    void cashAfterSnapshotLock_reconciliationFlagsLockedSnapshotConflict() {
        loanService.postCashRepayment(theLoan().getId(),
                new JciEccsCashRepaymentRequest(new BigDecimal("20000"), BigDecimal.ZERO, LocalDate.of(2034, 3, 20), "RCPT-1", "IDEMP-RECON-1"),
                employee.getId());

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.LOCKED_SNAPSHOT_CONFLICT);
        assertThat(principalRow.getReasonCode()).isEqualTo(JciEccsReconciliationReasonCode.CASH_RECOVERY_AFTER_SNAPSHOT_LOCK);
    }

    @Test
    void reversal_reconciliationFlagsReversed_loanReconciliationStillMatches() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-REV"))),
                employee.getId());
        var recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());
        var recoveryToReverse = recoveries.get(0);
        // SEC-004 (docs/security/SEC_001_002_REMEDIATION.md pattern): maker != checker.
        recoveryService.reverseRecovery(recoveryToReverse.getId(), "Employer requested reversal", employee.getId() + 1_000_000L);

        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);
        assertThat(principalRow.getStatus()).isEqualTo(JciEccsReconciliationStatus.REVERSED);

        var loanRecon = reconciliationService.reconcileLoan(theLoan().getId());
        assertThat(loanRecon.status()).isEqualTo(JciEccsReconciliationStatus.MATCHED);
        assertThat(loanRecon.outstandingVariance()).isEqualByComparingTo("0");
    }

    @Test
    void duplicateReconciliationRun_doesNotDuplicateRows() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-DUP"))),
                employee.getId());

        reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());

        List<JciEccsReconciliation> rows = reconciliationRepository.findByMember_IdOrderByDetectedAtDesc(member.getId());
        assertThat(rows).hasSize(3); // still exactly thrift + interest + principal, not 6
    }

    @Test
    void loanBalanceMatches_whenNoMismatchExists() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-LOAN-OK"))),
                employee.getId());

        var result = reconciliationService.reconcileLoan(theLoan().getId());
        assertThat(result.status()).isEqualTo(JciEccsReconciliationStatus.MATCHED);
        assertThat(result.derivedOutstanding()).isEqualByComparingTo(result.storedOutstanding());

        var integrityFindings = integrityCheckService.checkLoan(theLoan().getId());
        assertThat(integrityFindings).isEmpty(); // no exceptions when everything matches
    }

    @Test
    void manualResolution_requiresRemarks_recordsAuditFields() {
        debitConfirmationService.confirmDebit(payrollRunId,
                new JciEccsDebitConfirmationRequest(List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(),
                        JciEccsDebitStatus.DEBIT_PARTIAL, new BigDecimal("1300.00"), "TXN-RESOLVE"))),
                employee.getId());
        var summary = reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var principalRow = rowFor(summary, JciEccsRecoveryComponent.TERM_PRINCIPAL);

        assertThatThrownBy(() -> reconciliationService.resolveException(principalRow.getId(),
                JciEccsReconciliationResolutionAction.ACKNOWLEDGE, "   ", employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);

        var resolved = reconciliationService.resolveException(principalRow.getId(), JciEccsReconciliationResolutionAction.ACKNOWLEDGE,
                "Awaiting next payroll cycle for the shortfall", employee.getId());
        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.getResolvedBy()).isEqualTo(employee.getId());
        assertThat(resolved.getResolutionAction()).isEqualTo(JciEccsReconciliationResolutionAction.ACKNOWLEDGE);
        assertThat(resolved.getResolvedAt()).isNotNull();

        // A resolved row is never silently overwritten by a repeat run.
        reconciliationService.reconcilePayrollRun(payrollRunId, employee.getId());
        var stillResolved = reconciliationRepository.findById(principalRow.getId()).orElseThrow();
        assertThat(stillResolved.isResolved()).isTrue();
    }

}
