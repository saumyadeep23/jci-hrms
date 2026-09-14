package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanBatchRecoveryOutcome;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanSettlementMode;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.repository.CpfLoanBatchRecoveryRepository;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3 Parts 45/46/71 Scenarios 1 &amp; 2 - the two MANDATORY business scenarios, exercised end-to-end
 * through the real CpfLoanApplicationService -&gt; CpfLedgerSyncService -&gt; CpfLoanSettlementService
 * pipeline (never mocked), following the exact same fixture conventions as
 * CpfLoanTwoPhaseAndSettlementTest (2055-2056 fin year, kept clear of the one real notified rate).
 */
@SpringBootTest
@Transactional
class CpfLoanRecoveryScenariosTest {

    @Autowired private CpfLoanApplicationService loanApplicationService;
    @Autowired private CpfLoanSettlementService loanSettlementService;
    @Autowired private CpfLedgerSyncService ledgerSyncService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;
    @Autowired private CpfLoanBatchRecoveryRepository batchRecoveryRepository;

    private static final LocalDate SANCTION_DATE = LocalDate.of(2055, 6, 15);

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFRECOVSC", "CPF Recovery Scenario Test Dept"));
        designation = designationRepository.save(new Designation("CPF Recovery Scenario Test Officer"));
        rateRepository.save(new CpfStatutoryInterestRate("2055-2056", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "EPFO/RATIF/2055-56/SCEN", LocalDate.of(2055, 4, 1)));
    }

    private Employee newEmployee(String code, String email) {
        return employeeRepository.save(new Employee(code, "Recovery", "Tester", email, LocalDate.of(1988, 1, 1), department, designation));
    }

    private void seedBalance(Employee employee, BigDecimal ee) {
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(employee, "2019-2020", LocalDate.of(2020, 1, 1),
                CpfLedgerEntryType.OPENING_BALANCE, ee, BigDecimal.ZERO, BigDecimal.ZERO, ee));
    }

    private PayrollBatch payrollBatchFor(YearMonth period, String batchNo) {
        return payrollBatchRepository.save(new PayrollBatch(batchNo, period.getMonthValue(), period.getYear(),
                IncomingFundTransferService.financialYearFor(period.atEndOfMonth())));
    }

    private PayrollMonthlyRecord recordFor(PayrollBatch batch, Employee employee, YearMonth period) {
        return payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                period.getMonthValue(), period.getYear(), "01", "MGR", "X", "IDA", 30));
    }

    /**
     * "Employee 8" scenario: a loan disbursed 20-09-2026-equivalent must resolve zero recovery for its own
     * disbursement month regardless of that batch's own processing status, and the first principal
     * instalment only from the following month.
     */
    @Test
    void employee8Scenario_disbursementMonth_zeroRecovery_nextMonth_firstPrincipalInstallment() {
        Employee employee = newEmployee("EMP-CPFSCEN-8", "cpfscen8@example.com");
        seedBalance(employee, new BigDecimal("50000.00"));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("24000.00"), 12, "Scenario 1"), null);
        loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("24000.00"), "SANC/SCEN8", SANCTION_DATE, 12, null, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);

        // The disbursement's own month, per CpfLedgerSyncService always stamping "now" as the valueDate.
        YearMonth disbursementMonth = YearMonth.now();
        YearMonth sameMonthBatchPeriod = disbursementMonth;
        YearMonth nextMonthBatchPeriod = disbursementMonth.plusMonths(1);

        // Same-month batch: the resolver (exercised directly here, exactly as
        // PayrollBatchComputationService itself would call it) must say zero - so payroll's own
        // addHeadItem() convention ("only non-zero heads are inserted") means Head 30 never even gets a
        // row for this batch. No CpfLedgerSyncService.disburse() call is needed to prove this: there is
        // nothing for it to act on.
        PayrollBatch sameMonthBatch = payrollBatchFor(sameMonthBatchPeriod, "BATCH-SCEN8-SAME");
        var resolverForSameMonth = resolverBean().resolve(employee, sameMonthBatch);
        assertThat(resolverForSameMonth.principal()).isEqualByComparingTo("0");
        assertThat(resolverForSameMonth.interest()).isEqualByComparingTo("0");

        // Next month: resolver says the first instalment is due; simulate payroll actually posting that
        // amount as Head 30, then run the real CpfLedgerSyncService end to end.
        var resolverForNextMonth = resolverBean().resolve(employee, payrollBatchStub(nextMonthBatchPeriod));
        assertThat(resolverForNextMonth.principal()).isEqualByComparingTo("2000.00"); // 24000 / 12

        PayrollBatch nextMonthBatch = payrollBatchFor(nextMonthBatchPeriod, "BATCH-SCEN8-NEXT");
        PayrollMonthlyRecord record = recordFor(nextMonthBatch, employee, nextMonthBatchPeriod);
        headItemRepository.save(new PayrollMonthlyHeadItem(record, 30, resolverForNextMonth.principal()));
        nextMonthBatch.setStatus(PayrollBatchStatus.HR_FINALIZED);
        ledgerSyncService.disburse(nextMonthBatch.getId(), null);

        CpfLoanApplicationResponse afterFirstRecovery = loanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterFirstRecovery.outstandingBalance()).isEqualByComparingTo("22000.00"); // 24000 - 2000
        assertThat(afterFirstRecovery.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.PRINCIPAL);
    }

    /**
     * "Employee 108" scenario: full principal+interest cash settlement posted before September payroll
     * recovery is finalized/synced must zero out that period's actual recovery, close the loan, and leave
     * an auditable trace of the now-excess payroll-computed amount rather than posting it or silently
     * dropping it.
     */
    @Test
    void employee108Scenario_fullCashSettlementBeforePayrollSync_zeroesRecoveryAndClosesLoan() {
        Employee employee = newEmployee("EMP-CPFSCEN-108", "cpfscen108@example.com");
        seedBalance(employee, new BigDecimal("50000.00"));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("6000.00"), 1, "Scenario 2"), null);
        CpfLoanApplicationResponse sanctioned = loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("6000.00"), "SANC/SCEN108", SANCTION_DATE, 1, 1, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);
        BigDecimal outstandingInterestAtDisbursement = sanctioned.totalInterestAmount();

        // Payroll has already computed September's Head 30 amount (the full 6000.00, one instalment) into
        // a not-yet-finalized batch, BEFORE the employee's cash settlement is posted - mirroring "recovery
        // generated but payroll not yet finalized" from the spec's own scenario wording.
        YearMonth septemberEquivalent = YearMonth.now().plusMonths(1);
        PayrollBatch septemberBatch = payrollBatchFor(septemberEquivalent, "BATCH-SCEN108-SEP");
        PayrollMonthlyRecord record = recordFor(septemberBatch, employee, septemberEquivalent);
        headItemRepository.save(new PayrollMonthlyHeadItem(record, 30, new BigDecimal("6000.00")));

        long ledgerRepaymentCountBeforeSettlement = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT).count();

        // Full cash settlement posted now (before September payroll is finalized).
        CpfLoanSettlementRequest settlementRequest = new CpfLoanSettlementRequest(new BigDecimal("6000.00"), outstandingInterestAtDisbursement,
                CpfLoanSettlementMode.CASH, "CASH-RECEIPT-SCEN108", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "Full settlement before payroll finalization");
        var settlement = loanSettlementService.processCashSettlement(applied.id(), settlementRequest, null);
        assertThat(settlement.isEarlyForeclosure()).isTrue();

        CpfLoanApplicationResponse afterSettlement = loanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterSettlement.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(afterSettlement.outstandingInterest()).isEqualByComparingTo("0.00");
        assertThat(afterSettlement.status()).isEqualTo(CpfLoanApplicationStatus.CLOSED);

        // September payroll now proceeds to finalization/sync - the stale Head 30 amount must resolve to
        // ZERO actual recovery: no new ledger entry, loan stays exactly as the settlement left it.
        septemberBatch.setStatus(PayrollBatchStatus.HR_FINALIZED);
        ledgerSyncService.disburse(septemberBatch.getId(), null);

        long ledgerRepaymentCountAfterSync = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT).count();
        // Only the settlement's own two entries (principal + interest, both credited to EE per the
        // confirmed business decision) - nothing added by the payroll sync afterward.
        assertThat(ledgerRepaymentCountAfterSync).isEqualTo(ledgerRepaymentCountBeforeSettlement + 2);

        CpfLoanApplicationResponse afterSync = loanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterSync.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(afterSync.status()).isEqualTo(CpfLoanApplicationStatus.CLOSED); // no status regression

        // The already-payroll-computed 6000.00 is traceable, not silently dropped.
        var excessRow = batchRecoveryRepository.findByLoan_IdAndPayrollBatch_IdAndRecoveryPhase(applied.id(), septemberBatch.getId(), CpfLoanRecoveryPhase.PRINCIPAL)
                .orElseThrow();
        assertThat(excessRow.getOutcome()).isEqualTo(CpfLoanBatchRecoveryOutcome.EXCESS_LOAN_ALREADY_CLOSED);
        assertThat(excessRow.getAmountRecovered()).isEqualByComparingTo("6000.00");
    }

    @Test
    void cashSettlement_retriedWithSameInstrumentNumber_isIdempotent_doesNotDoubleSettle() {
        Employee employee = newEmployee("EMP-CPFSCEN-IDEMP", "cpfscenidemp@example.com");
        seedBalance(employee, new BigDecimal("50000.00"));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("3000.00"), 1, "Idempotency"), null);
        CpfLoanApplicationResponse sanctioned = loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("3000.00"), "SANC/IDEMP", SANCTION_DATE, 1, 1, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);

        CpfLoanSettlementRequest request = new CpfLoanSettlementRequest(new BigDecimal("3000.00"), sanctioned.totalInterestAmount(),
                CpfLoanSettlementMode.CASH, "CASH-RECEIPT-IDEMP-001", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "First attempt");

        var first = loanSettlementService.processCashSettlement(applied.id(), request, null);
        var retry = loanSettlementService.processCashSettlement(applied.id(), request, null);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.receiptVoucherNo()).isEqualTo(first.receiptVoucherNo());

        long settlementLedgerEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT).count();
        assertThat(settlementLedgerEntries).isEqualTo(2); // principal + interest, from the first call only - not duplicated by the retry
    }

    @Test
    void payrollRecoverySync_reRunOnSameBatch_isIdempotent_doesNotDoubleRecover() {
        Employee employee = newEmployee("EMP-CPFSC-SYNC", "cpfscensyncidemp@example.com");
        seedBalance(employee, new BigDecimal("50000.00"));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("4000.00"), 2, "Sync idempotency"), null);
        loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("4000.00"), "SANC/SYNCIDEMP", SANCTION_DATE, 2, 1, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);

        YearMonth period = YearMonth.now().plusMonths(1);
        PayrollBatch batch = payrollBatchFor(period, "BATCH-SYNCIDEMP");
        PayrollMonthlyRecord record = recordFor(batch, employee, period);
        headItemRepository.save(new PayrollMonthlyHeadItem(record, 30, new BigDecimal("2000.00")));
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        ledgerSyncService.disburse(batch.getId(), null);
        // Re-running syncForBatch against the same already-DISBURSED batch (its own javadoc says this is
        // supported, e.g. a re-run) must not recover the same instalment twice.
        ledgerSyncService.syncForBatch(payrollBatchRepository.findById(batch.getId()).orElseThrow());

        CpfLoanApplicationResponse afterReSync = loanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterReSync.outstandingBalance()).isEqualByComparingTo("2000.00"); // 4000 - 2000, only once

        long principalRecoveryEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT).count();
        assertThat(principalRecoveryEntries).isEqualTo(1);
    }

    /**
     * Business decision (confirmed): cash-settled interest is credited to the member's own EE share, the
     * same accounting treatment as payroll-recovered interest (CpfLedgerSyncService's own INTEREST-phase
     * posting) - never left unposted, and never crediting the interest REBATE itself (that portion is a
     * waiver, not a payment received).
     */
    @Test
    void cashSettlement_interestPortion_isCreditedToEeShare_sameAsPayrollRecoveredInterest() {
        Employee employee = newEmployee("EMP-CPFSC-INTCR", "cpfscintcredit@example.com");
        seedBalance(employee, new BigDecimal("50000.00"));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("6000.00"), 1, "Interest credit check"), null);
        CpfLoanApplicationResponse sanctioned = loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("6000.00"), "SANC/INTCR", SANCTION_DATE, 1, 1, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);
        BigDecimal outstandingInterest = sanctioned.totalInterestAmount();
        assertThat(outstandingInterest.signum()).isPositive();

        CpfLoanSettlementRequest settlementRequest = new CpfLoanSettlementRequest(new BigDecimal("6000.00"), outstandingInterest,
                CpfLoanSettlementMode.CASH, "CASH-RECEIPT-INTCR", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "Full settlement");
        loanSettlementService.processCashSettlement(applied.id(), settlementRequest, null);

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT).toList();
        assertThat(entries).hasSize(2); // principal entry, then interest entry

        CpfTrustMemberLedgerEntry principalEntry = entries.get(0);
        CpfTrustMemberLedgerEntry interestEntry = entries.get(1);

        assertThat(principalEntry.getLoanRepayPrincipal()).isEqualByComparingTo("6000.00");
        assertThat(principalEntry.getRunningEeBalance()).isEqualByComparingTo("50000.00"); // 44000 (post-disbursement) + 6000 restored

        assertThat(interestEntry.getLoanRepayPrincipal()).isEqualByComparingTo("0.00"); // this entry is interest-only, not a second principal credit
        assertThat(interestEntry.getInterestCredit()).isEqualByComparingTo(outstandingInterest);
        assertThat(interestEntry.getLoanRepayInterest()).isEqualByComparingTo(outstandingInterest);
        assertThat(interestEntry.getEeShareCredit()).isEqualByComparingTo(outstandingInterest);
        assertThat(interestEntry.getRunningEeBalance()).isEqualByComparingTo(new BigDecimal("50000.00").add(outstandingInterest));
        // Interest was never part of the tracked Refundable Loan principal balance.
        assertThat(interestEntry.getRunningLoanCpfBalance()).isEqualByComparingTo("0.00");

        CpfLoanApplicationResponse reloaded = loanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(reloaded.outstandingInterest()).isEqualByComparingTo("0.00");
        assertThat(reloaded.status()).isEqualTo(CpfLoanApplicationStatus.CLOSED);
    }

    /** Same underlying bean as ledgerSyncService's own collaborator - fetched fresh per call for readability at each call site above. */
    private CpfLoanPayrollRecoveryResolverService resolverBean() {
        return applicationContext.getBean(CpfLoanPayrollRecoveryResolverService.class);
    }

    @Autowired private org.springframework.context.ApplicationContext applicationContext;

    /** A transient (unsaved) PayrollBatch is fine for a pure resolve() call - the resolver never persists anything, only reads the loan. */
    private PayrollBatch payrollBatchStub(YearMonth period) {
        return new PayrollBatch("STUB-" + period, period.getMonthValue(), period.getYear(), IncomingFundTransferService.financialYearFor(period.atEndOfMonth()));
    }
}
