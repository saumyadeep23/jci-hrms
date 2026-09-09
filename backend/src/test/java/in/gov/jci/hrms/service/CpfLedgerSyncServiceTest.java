package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
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
 * Real-DB integration test (matching EmployeeFamilyNomineeUpdateTest's own pattern) for the post-payroll
 * CPF Trust ledger sync hook - proves Head 27/28/29 (CPF/VPF/ARR_CPF) and Stat Head 3/12 (JCPF/Arrear_JCPF)
 * extraction posts a correct PAYROLL_MONTHLY ledger row, and Head 30/51 (CPFLOAN_PRIN/NREFLOAN_PRIN)
 * extraction both reduces an active CpfLoanApplication's own outstanding_balance and posts a LOAN_REPAYMENT row -
 * exactly as CpfLedgerSyncService's own javadoc describes. See that class's javadoc for why this test
 * drives the sync via disburse()/syncForBatch() directly rather than through the (separately, newly
 * built) PayrollBatchController.disburse endpoint.
 */
@SpringBootTest
@Transactional
class CpfLedgerSyncServiceTest {

    @Autowired private CpfLedgerSyncService cpfLedgerSyncService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;
    @Autowired private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfLoanApplicationRepository cpfLoanApplicationRepository;

    private Employee employee;
    private PayrollBatch batch;
    private PayrollMonthlyRecord record;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFSYNC", "CPF Sync Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Sync Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFSYNC-1", "Ratan", "Dey",
                "ratan.dey.cpfsync@example.com", LocalDate.of(1987, 6, 15), department, designation));

        batch = payrollBatchRepository.save(new PayrollBatch("BATCH-CPFSYNC-1", 6, 2026, "2026-2027"));
        record = payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                6, 2026, "01", "MGR", "X", "IDA", 30));
    }

    private void giveHeadAmount(int headCount, BigDecimal amount) {
        headItemRepository.save(new PayrollMonthlyHeadItem(record, headCount, amount));
    }

    private void giveStatAmount(int statHeadCount, BigDecimal amount) {
        statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, statHeadCount, amount));
    }

    @Test
    void syncForBatch_extractsCpfVpfAndJcpfHeads_postsPayrollMonthlyLedgerEntry() {
        giveHeadAmount(27, new BigDecimal("1200.00")); // CPF
        giveHeadAmount(28, new BigDecimal("500.00"));  // VPF
        giveStatAmount(3, new BigDecimal("1200.00"));  // JCPF (employer match)
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        cpfLedgerSyncService.disburse(batch.getId(), null);

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        assertThat(entries).hasSize(1);
        CpfTrustMemberLedgerEntry entry = entries.get(0);
        assertThat(entry.getEntryType()).isEqualTo(CpfLedgerEntryType.PAYROLL_MONTHLY);
        assertThat(entry.getEeShareCredit()).isEqualByComparingTo("1200.00");
        assertThat(entry.getVpfCredit()).isEqualByComparingTo("500.00");
        assertThat(entry.getErShareCredit()).isEqualByComparingTo("1200.00");
        assertThat(entry.getRunningEeBalance()).isEqualByComparingTo("1200.00");
        assertThat(entry.getRunningVpfBalance()).isEqualByComparingTo("500.00");
        assertThat(entry.getRunningErBalance()).isEqualByComparingTo("1200.00");
        assertThat(entry.getValueDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(entry.getSalMonth()).isEqualTo(6);
        assertThat(entry.getSalYear()).isEqualTo(2026);
    }

    @Test
    void syncForBatch_includesArrearCpfAndArrearJcpfHeads() {
        giveHeadAmount(29, new BigDecimal("300.00"));  // ARR_CPF
        giveStatAmount(12, new BigDecimal("300.00"));  // Arrear_JCPF
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        cpfLedgerSyncService.disburse(batch.getId(), null);

        CpfTrustMemberLedgerEntry entry = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).get(0);
        assertThat(entry.getEeShareCredit()).isEqualByComparingTo("300.00");
        assertThat(entry.getErShareCredit()).isEqualByComparingTo("300.00");
    }

    @Test
    void syncForBatch_activeLoanWithRecoveryHead_reducesBalanceAndPostsLoanRepaymentEntry() {
        CpfLoanApplication loan = new CpfLoanApplication("CPFLOAN-CPFSYNC-1", employee, CpfLoanType.REFUNDABLE_LOAN,
                "House Repair", new BigDecimal("5000.00"));
        loan.setOutstandingBalance(new BigDecimal("5000.00"));
        loan.setTotalInstallments(5);
        loan.setStatus(CpfLoanApplicationStatus.DISBURSED);
        loan = cpfLoanApplicationRepository.save(loan);

        giveHeadAmount(30, new BigDecimal("1000.00")); // CPFLOAN_PRIN
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        cpfLedgerSyncService.disburse(batch.getId(), null);

        CpfLoanApplication reloaded = cpfLoanApplicationRepository.findById(loan.getId()).orElseThrow();
        assertThat(reloaded.getOutstandingBalance()).isEqualByComparingTo("4000.00");
        assertThat(reloaded.getRecoveredInstallments()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(CpfLoanApplicationStatus.DISBURSED);

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_REPAYMENT);
            assertThat(e.getEeShareCredit()).isEqualByComparingTo("1000.00");
            assertThat(e.getLoan()).isNotNull();
            assertThat(e.getLoan().getId()).isEqualTo(reloaded.getId());
        });
    }

    @Test
    void syncForBatch_recoveryClearsOutstandingBalance_closesLoan() {
        CpfLoanApplication loan = new CpfLoanApplication("CPFLOAN-CPFSYNC-2", employee, CpfLoanType.REFUNDABLE_LOAN,
                "Medical Emergency", new BigDecimal("1000.00"));
        loan.setOutstandingBalance(new BigDecimal("1000.00"));
        loan.setTotalInstallments(1);
        loan.setStatus(CpfLoanApplicationStatus.DISBURSED);
        loan = cpfLoanApplicationRepository.save(loan);

        giveHeadAmount(30, new BigDecimal("1000.00"));
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        cpfLedgerSyncService.disburse(batch.getId(), null);

        CpfLoanApplication reloaded = cpfLoanApplicationRepository.findById(loan.getId()).orElseThrow();
        assertThat(reloaded.getOutstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(reloaded.getStatus()).isEqualTo(CpfLoanApplicationStatus.CLOSED);
    }

    @Test
    void disburse_notHrFinalized_throws() {
        assertThat(batch.getStatus()).isEqualTo(PayrollBatchStatus.DRAFT);

        assertThatThrownBy(() -> cpfLedgerSyncService.disburse(batch.getId(), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void syncForBatch_batchNotDisbursed_throws() {
        assertThatThrownBy(() -> cpfLedgerSyncService.syncForBatch(batch))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
