package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
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
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfLoanBatchRecoveryRepository;
import in.gov.jci.hrms.repository.CpfLoanSettlementRepository;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3 Part 48 - real, genuinely concurrent (two OS threads, two separate transactions/connections)
 * payroll-recovery-vs-cash-settlement race against the same loan. Deliberately NOT @Transactional at the
 * class level: a Spring-managed test transaction is bound to the main test thread only, so fixture data
 * created inside one would be invisible to the worker threads' own separate connections (each gets its own
 * real, committed transaction via the normal @Transactional service proxies) - this class commits its own
 * fixtures in setUp() and cleans them up explicitly in tearDown() instead of relying on rollback.
 */
@SpringBootTest
class CpfLoanConcurrencyTest {

    @Autowired private CpfLoanApplicationService loanApplicationService;
    @Autowired private CpfLoanSettlementService loanSettlementService;
    @Autowired private CpfLedgerSyncService ledgerSyncService;
    @Autowired private CpfLoanApplicationRepository loanApplicationRepository;
    @Autowired private CpfLoanSettlementRepository loanSettlementRepository;
    @Autowired private CpfLoanBatchRecoveryRepository batchRecoveryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;

    private static final LocalDate SANCTION_DATE = LocalDate.of(2056, 6, 15);

    private Employee employee;
    private Department department;
    private Designation designation;
    private Long rateId;
    private Long loanId;
    private Long batchId;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFCONC", "CPF Concurrency Test Dept"));
        designation = designationRepository.save(new Designation("CPF Concurrency Test Officer"));
        rateId = rateRepository.save(new CpfStatutoryInterestRate("2056-2057", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "EPFO/RATIF/2056-57/CONC", LocalDate.of(2056, 4, 1))).getId();
        employee = employeeRepository.save(new Employee("EMP-CPFCONC-1", "Race", "Tester", "cpfconc1@example.com",
                LocalDate.of(1988, 1, 1), department, designation));
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(employee, "2019-2020", LocalDate.of(2020, 1, 1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000.00")));

        CpfLoanApplicationResponse applied = loanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("5000.00"), 1, "Concurrency race"), null);
        CpfLoanApplicationResponse sanctioned = loanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("5000.00"), "SANC/CONC", SANCTION_DATE, 1, 1, null, null, null), null);
        loanApplicationService.disburseLoan(applied.id(), null);
        loanId = applied.id();

        YearMonth period = YearMonth.now().plusMonths(1);
        PayrollBatch batch = payrollBatchRepository.save(new PayrollBatch("BATCH-CONC-RACE", period.getMonthValue(), period.getYear(),
                IncomingFundTransferService.financialYearFor(period.atEndOfMonth())));
        PayrollMonthlyRecord record = payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                period.getMonthValue(), period.getYear(), "01", "MGR", "X", "IDA", 30));
        headItemRepository.save(new PayrollMonthlyHeadItem(record, 30, new BigDecimal("5000.00")));
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);
        payrollBatchRepository.saveAndFlush(batch);
        batchId = batch.getId();
    }

    /**
     * Every step below is independently guarded: this class commits its own fixtures (no @Transactional
     * rollback safety net - see the class javadoc), so if setUp() itself fails partway through, later
     * steps here must still run to avoid leaking a row that collides with the NEXT run's own insert
     * (e.g. the department's own unique code). A single unguarded NPE/exception on an earlier, unset
     * field must never skip cleanup of fixtures that WERE created.
     */
    @AfterEach
    void tearDown() {
        attempt(() -> { if (loanId != null) batchRecoveryRepository.findAll().stream().filter(r -> r.getLoan().getId().equals(loanId)).forEach(batchRecoveryRepository::delete); });
        attempt(() -> { if (loanId != null) loanSettlementRepository.findByLoanIdOrderByCreatedAtDesc(loanId).forEach(loanSettlementRepository::delete); });
        attempt(() -> { if (employee != null) ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).forEach(ledgerRepository::delete); });
        attempt(() -> { if (loanId != null) loanApplicationRepository.deleteById(loanId); });
        attempt(() -> { if (batchId != null) payrollMonthlyRecordRepository.findByBatch_Id(batchId).forEach(r -> headItemRepository.findByRecord_TranId(r.getTranId()).forEach(headItemRepository::delete)); });
        attempt(() -> { if (batchId != null) payrollMonthlyRecordRepository.findByBatch_Id(batchId).forEach(payrollMonthlyRecordRepository::delete); });
        attempt(() -> { if (batchId != null) payrollBatchRepository.deleteById(batchId); });
        attempt(() -> { if (employee != null) employeeRepository.delete(employee); });
        attempt(() -> { if (rateId != null) rateRepository.deleteById(rateId); });
        attempt(() -> { if (department != null) departmentRepository.delete(department); });
        attempt(() -> { if (designation != null) designationRepository.delete(designation); });
    }

    private void attempt(Runnable step) {
        try {
            step.run();
        } catch (Exception ignored) {
            // best-effort cleanup - a failure in one step must never skip the rest (see this method's own javadoc)
        }
    }

    /**
     * Two real threads race to consume the same 5000.00 outstanding principal: one via payroll recovery
     * (CpfLedgerSyncService.disburse -&gt; applyTwoPhaseLoanRecovery's pessimistic lock), the other via a
     * full cash settlement (CpfLoanSettlementService.processCashSettlement's own matching lock). Exactly
     * one must actually consume the balance; the other must either safely no-op (payroll recovery routes
     * an already-closed loan to the excess/audit trail) or be cleanly rejected (settlement throws once the
     * loan is no longer DISBURSED) - never both mutating, never a negative balance, never two
     * LOAN_REPAYMENT ledger rows for the same 5000.00.
     */
    @Test
    void payrollRecoveryAndCashSettlement_concurrentlyRaceTheSameLoan_neverDoubleConsumeTheBalance() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Object> payrollRecovery = CompletableFuture.supplyAsync(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    return ledgerSyncService.disburse(batchId, null);
                } catch (Exception e) {
                    return e;
                }
            }, executor);

            CompletableFuture<Object> cashSettlement = CompletableFuture.supplyAsync(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    CpfLoanSettlementRequest request = new CpfLoanSettlementRequest(new BigDecimal("5000.00"), BigDecimal.ZERO,
                            CpfLoanSettlementMode.CASH, "CASH-RECEIPT-CONC-001", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "Concurrent settlement");
                    return loanSettlementService.processCashSettlement(loanId, request, null);
                } catch (Exception e) {
                    return e;
                }
            }, executor);

            Object payrollResult = payrollRecovery.get(20, TimeUnit.SECONDS);
            Object settlementResult = cashSettlement.get(20, TimeUnit.SECONDS);

            // Whichever lost the race either threw a clean, expected business exception, or (payroll
            // recovery's own case) simply completed without touching an already-closed loan - never an
            // unexpected error type such as a raw constraint violation leaking out of the lock.
            if (payrollResult instanceof Exception e) {
                assertThat(e).isInstanceOf(BusinessRuleViolationException.class);
            }
            if (settlementResult instanceof Exception e) {
                assertThat(e).isInstanceOf(BusinessRuleViolationException.class);
            }

            in.gov.jci.hrms.entity.CpfLoanApplication finalLoan = loanApplicationRepository.findById(loanId).orElseThrow();
            assertThat(finalLoan.getOutstandingBalance()).isEqualByComparingTo("0.00");
            assertThat(finalLoan.getOutstandingBalance().signum()).isGreaterThanOrEqualTo(0); // never negative

            List<CpfTrustMemberLedgerEntry> repaymentEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                    .filter(entry -> entry.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT)
                    .toList();
            // Exactly one of the two operations actually credited the ledger - never both (no double recovery).
            assertThat(repaymentEntries).hasSizeLessThanOrEqualTo(1);
            if (!repaymentEntries.isEmpty()) {
                assertThat(repaymentEntries.get(0).getRunningEeBalance()).isEqualByComparingTo("50000.00"); // fully restored exactly once, not twice
            }

            assertThat(finalLoan.getStatus()).isIn(CpfLoanApplicationStatus.CLOSED, CpfLoanApplicationStatus.DISBURSED);
        } finally {
            executor.shutdownNow();
        }
    }
}
