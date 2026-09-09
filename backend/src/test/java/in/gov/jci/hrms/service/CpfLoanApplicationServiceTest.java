package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanEligibilityResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB integration test (matching this task's other new tests) for the CPF Trust loan/withdrawal
 * origination lifecycle - applyLoan() -> sanctionLoan() -> disburseLoan(), the 75%-of-(EE+VPF) statutory
 * cap, the Refundable Guard (no second REFUNDABLE_LOAN while an earlier one still has a positive
 * outstanding balance), the LOAN_WITHDRAWAL ledger debit disburseLoan() posts, and that
 * CpfLedgerSyncService's existing payroll-hook loan recovery (built the previous task) correctly closes
 * a loan originated through this new service once its outstanding_balance reaches zero.
 */
@SpringBootTest
@Transactional
class CpfLoanApplicationServiceTest {

    @Autowired private CpfLoanApplicationService cpfLoanApplicationService;
    @Autowired private CpfLedgerSyncService cpfLedgerSyncService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;

    private Employee employee;

    // Far-future, fixed FY (rather than LocalDate.now()) so sanctionLoan()'s rate resolution never
    // collides with the live dev DB's one real pre-seeded notification (FY 2026-2027 - see
    // CpfStatutoryInterestRate's own header comment) and stays correct regardless of wall-clock date.
    private static final LocalDate SANCTION_DATE = LocalDate.of(2050, 6, 15);

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFLOAN", "CPF Loan Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Loan Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFLOAN-1", "Bimal", "Roy",
                "bimal.roy.cpfloan@example.com", LocalDate.of(1986, 9, 1), department, designation));
        rateRepository.save(new CpfStatutoryInterestRate("2050-2051", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "EPFO/RATIF/2050-51/01", LocalDate.of(2050, 4, 1)));
    }

    private void seedBalance(BigDecimal ee, BigDecimal vpf) {
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, "2025-2026", LocalDate.of(2026, 1, 31),
                CpfLedgerEntryType.OPENING_BALANCE, ee, BigDecimal.ZERO, vpf, ee.add(vpf));
        ledgerRepository.save(entry);
    }

    private CpfLoanApplicationRequest applicationRequest(CpfLoanType type, BigDecimal amount) {
        return new CpfLoanApplicationRequest(employee.getId(), type, "HOUSING", amount, 10, "Home repair");
    }

    @Test
    void checkEligibility_capsAt75PercentOfEeAndVpf() {
        seedBalance(new BigDecimal("10000.00"), new BigDecimal("2000.00"));

        CpfLoanEligibilityResponse eligibility = cpfLoanApplicationService.checkEligibility(employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING");

        assertThat(eligibility.totalEligibleCorpus()).isEqualByComparingTo("12000.00");
        // (10000 + 2000) * 0.75 = 9000.00
        assertThat(eligibility.maxPermissibleAmount()).isEqualByComparingTo("9000.00");
        assertThat(eligibility.activeLoanExists()).isFalse();
    }

    @Test
    void applyLoan_exceedingCap_throws() {
        seedBalance(new BigDecimal("10000.00"), BigDecimal.ZERO);
        // Cap is 7500.00; applying for 8000 must be rejected.
        CpfLoanApplicationRequest request = applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("8000.00"));

        assertThatThrownBy(() -> cpfLoanApplicationService.applyLoan(request, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void checkEligibility_blocksSecondRefundableLoanWithActiveOutstandingBalance() {
        seedBalance(new BigDecimal("20000.00"), BigDecimal.ZERO);
        CpfLoanApplicationResponse first = cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("5000.00")), null);
        cpfLoanApplicationService.sanctionLoan(first.id(),
                new CpfLoanSanctionRequest(new BigDecimal("5000.00"), "SANC/001", SANCTION_DATE, 10, 10), null);
        cpfLoanApplicationService.disburseLoan(first.id(), null);

        CpfLoanEligibilityResponse eligibility = cpfLoanApplicationService.checkEligibility(employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING");
        assertThat(eligibility.activeLoanExists()).isTrue();
        assertThat(eligibility.outstandingActiveLoanBalance()).isEqualByComparingTo("5000.00");

        assertThatThrownBy(() -> cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("1000.00")), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void loanLifecycle_appliedToSanctionedToDisbursed() {
        seedBalance(new BigDecimal("20000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("6000.00")), null);
        assertThat(applied.status()).isEqualTo(CpfLoanApplicationStatus.APPLIED);
        assertThat(applied.loanApplicationNo()).startsWith("CPFL/");
        // 6000 / 10 installments = 600.00 projected monthly recovery.
        assertThat(applied.monthlyRecoveryPrincipal()).isEqualByComparingTo("600.00");

        CpfLoanApplicationResponse sanctioned = cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("5000.00"), "SANC/LIFECYCLE/001", SANCTION_DATE, 5, 5), null);
        assertThat(sanctioned.status()).isEqualTo(CpfLoanApplicationStatus.SANCTIONED);
        assertThat(sanctioned.sanctionedAmount()).isEqualByComparingTo("5000.00");
        // Recomputed at sanction time: 5000 / 5 = 1000.00 (not the original 600.00).
        assertThat(sanctioned.monthlyRecoveryPrincipal()).isEqualByComparingTo("1000.00");
        // Rate resolved from the seeded FY 2050-2051 notification: base 8.25%, +1.00% markup = 9.25%.
        assertThat(sanctioned.baseCpfRate()).isEqualByComparingTo("8.25");
        assertThat(sanctioned.interestRate()).isEqualByComparingTo("9.25");
        // (installments+1) * amount * rate / 2400 = 6 * 5000 * 9.25 / 2400 = 115.63 (HALF_UP).
        assertThat(sanctioned.totalInterestAmount()).isEqualByComparingTo("115.63");
        assertThat(sanctioned.outstandingInterest()).isEqualByComparingTo("115.63");
        assertThat(sanctioned.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.PRINCIPAL);

        CpfLoanApplicationResponse disbursed = cpfLoanApplicationService.disburseLoan(applied.id(), null);
        assertThat(disbursed.status()).isEqualTo(CpfLoanApplicationStatus.DISBURSED);
        assertThat(disbursed.outstandingBalance()).isEqualByComparingTo("5000.00");
        assertThat(disbursed.recoveredInstallments()).isEqualTo(0);
        assertThat(disbursed.disbursedAt()).isNotNull();
    }

    @Test
    void disburseLoan_debitsEeFirstThenSpillsIntoVpf_andWritesRunningBalances() {
        // Only 3000 in EE, 5000 in VPF - a 5000 disbursement must debit all of EE (3000) then the
        // remaining 2000 from VPF.
        seedBalance(new BigDecimal("3000.00"), new BigDecimal("5000.00"));

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.NON_REFUNDABLE_WITHDRAWAL, new BigDecimal("5000.00")), null);
        cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("5000.00"), "SANC/SPILL/001", SANCTION_DATE, 1, 1), null);
        cpfLoanApplicationService.disburseLoan(applied.id(), null);

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry withdrawal = entries.get(entries.size() - 1);
        assertThat(withdrawal.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_WITHDRAWAL);
        assertThat(withdrawal.getEeShareDebit()).isEqualByComparingTo("3000.00");
        assertThat(withdrawal.getVpfDebit()).isEqualByComparingTo("2000.00");
        assertThat(withdrawal.getTotalDebit()).isEqualByComparingTo("5000.00");
        assertThat(withdrawal.getRunningEeBalance()).isEqualByComparingTo("0.00");
        assertThat(withdrawal.getRunningVpfBalance()).isEqualByComparingTo("3000.00");
        assertThat(withdrawal.getLoan()).isNotNull();
        assertThat(withdrawal.getLoan().getId()).isEqualTo(applied.id());
    }

    @Test
    void monthlyPayrollRecoveryHook_principalPhaseClears_flipsToInterestPhaseWithoutClosing() {
        seedBalance(new BigDecimal("10000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("1000.00")), null);
        cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("1000.00"), "SANC/CLOSE/001", SANCTION_DATE, 1, 1), null);
        cpfLoanApplicationService.disburseLoan(applied.id(), null);

        PayrollBatch batch = payrollBatchRepository.save(new PayrollBatch("BATCH-CPFLOAN-1", 7, 2026, "2026-2027"));
        PayrollMonthlyRecord record = payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch, employee,
                employee.getEmployeeCode(), 7, 2026, "01", "MGR", "X", "IDA", 31));
        headItemRepository.save(new PayrollMonthlyHeadItem(record, 30, new BigDecimal("1000.00"))); // CPFLOAN_PRIN, full payoff
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        cpfLedgerSyncService.disburse(batch.getId(), null);

        List<CpfLoanApplicationResponse> history = cpfLoanApplicationService.findByEmployee(employee.getId());
        CpfLoanApplicationResponse reloaded = history.stream().filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(reloaded.outstandingBalance()).isEqualByComparingTo("0.00");
        // Principal alone clearing is not enough to close a loan under two-phase recovery - it still owes
        // interest (Head 31), so it flips to the INTEREST phase and stays DISBURSED rather than closing.
        assertThat(reloaded.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.INTEREST);
        assertThat(reloaded.status()).isEqualTo(CpfLoanApplicationStatus.DISBURSED);
        assertThat(reloaded.outstandingInterest()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void rejectLoan_appliedApplication_setsRejectedWithRemarks() {
        seedBalance(new BigDecimal("10000.00"), BigDecimal.ZERO);
        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(
                applicationRequest(CpfLoanType.REFUNDABLE_LOAN, new BigDecimal("1000.00")), null);

        CpfLoanApplicationResponse rejected = cpfLoanApplicationService.rejectLoan(applied.id(), "Insufficient documentation", null);

        assertThat(rejected.status()).isEqualTo(CpfLoanApplicationStatus.REJECTED);
        assertThat(rejected.rejectionRemarks()).isEqualTo("Insufficient documentation");
    }
}
