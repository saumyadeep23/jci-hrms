package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementQuoteResponse;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
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
 * CPF Loan Origination, Two-Phase Payroll Recovery (Head 30 then Head 31), and Direct Out-of-Payroll Cash
 * Settlement. FY range (2055-2056) is picked far from the live dev DB's one real notified rate (FY
 * 2026-2027 - see CpfStatutoryInterestRate's own header comment) so seeding here never collides with it.
 */
@SpringBootTest
@Transactional
class CpfLoanTwoPhaseAndSettlementTest {

    @Autowired private CpfLoanApplicationService cpfLoanApplicationService;
    @Autowired private CpfLoanSettlementService cpfLoanSettlementService;
    @Autowired private CpfLedgerSyncService cpfLedgerSyncService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;

    private static final LocalDate SANCTION_DATE = LocalDate.of(2055, 6, 15);

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFL2P", "CPF Two-Phase Test Dept"));
        designation = designationRepository.save(new Designation("CPF Two-Phase Test Officer"));
        rateRepository.save(new CpfStatutoryInterestRate("2055-2056", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "EPFO/RATIF/2055-56/01", LocalDate.of(2055, 4, 1)));
    }

    private Employee newEmployee(String code, String email) {
        return employeeRepository.save(new Employee(code, "Loan", "Tester", email, LocalDate.of(1988, 1, 1), department, designation));
    }

    // Deliberately a real PAST date (not tied to SANCTION_DATE's fictional 2055 FY, which only exists to
    // pick a safe rate-notification year): disburseLoan()/CpfLedgerSyncService always stamp valueDate as
    // the actual wall-clock "now", so the opening balance must sort chronologically before that or every
    // "latest entry" lookup in this test file would resolve to this seed row instead.
    private void seedBalance(Employee employee, BigDecimal ee, BigDecimal vpf) {
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(employee, "2019-2020", LocalDate.of(2020, 1, 1),
                CpfLedgerEntryType.OPENING_BALANCE, ee, BigDecimal.ZERO, vpf, ee.add(vpf)));
    }

    // --- 1. 75% cap and duplicate refundable advance block ---

    @Test
    void applyLoan_exceeding75PercentCap_rejected() {
        Employee employee = newEmployee("EMP-CPFL2P-1", "cpfl2p1@example.com");
        seedBalance(employee, new BigDecimal("10000.00"), BigDecimal.ZERO);

        CpfLoanApplicationRequest request = new CpfLoanApplicationRequest(employee.getId(), CpfLoanType.REFUNDABLE_LOAN,
                "HOUSING", new BigDecimal("8000.00"), 12, "Home repair");

        assertThatThrownBy(() -> cpfLoanApplicationService.applyLoan(request, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void applyLoan_secondRefundableWhileFirstStillActive_blocked() {
        // The block is keyed on a positive outstandingBalance (see CpfLoanApplicationService's own
        // eligibility javadoc), which only exists once a loan is SANCTIONED - a merely-APPLIED loan is
        // still outstandingBalance = 0 and doesn't trip it, so the first loan must be sanctioned here too.
        Employee employee = newEmployee("EMP-CPFL2P-2", "cpfl2p2@example.com");
        seedBalance(employee, new BigDecimal("20000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse first = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(employee.getId(),
                CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("5000.00"), 10, "First advance"), null);
        cpfLoanApplicationService.sanctionLoan(first.id(),
                new CpfLoanSanctionRequest(new BigDecimal("5000.00"), "SANC/2P/BLOCK", SANCTION_DATE, 10, 10, null, null, null), null);

        assertThatThrownBy(() -> cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(employee.getId(),
                CpfLoanType.REFUNDABLE_LOAN, "MEDICAL", new BigDecimal("1000.00"), 5, "Second advance"), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // --- 2. Sanction calculation with +1.00% markup ---

    @Test
    void sanctionLoan_computesInterestWithNotifiedRatePlusOnePercentMarkup() {
        Employee employee = newEmployee("EMP-CPFL2P-3", "cpfl2p3@example.com");
        seedBalance(employee, new BigDecimal("20000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("12000.00"), 12, "Home repair"), null);
        CpfLoanApplicationResponse sanctioned = cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("12000.00"), "SANC/2P/001", SANCTION_DATE, 12, 12, null, null, null), null);

        assertThat(sanctioned.baseCpfRate()).isEqualByComparingTo("8.25");
        assertThat(sanctioned.interestRate()).isEqualByComparingTo("9.25");
        // (12+1) * 12000 * 9.25 / 2400 = 601.25
        assertThat(sanctioned.totalInterestAmount()).isEqualByComparingTo("601.25");
        assertThat(sanctioned.outstandingInterest()).isEqualByComparingTo("601.25");
        // 601.25 / 12 = 50.10 (HALF_UP)
        assertThat(sanctioned.monthlyRecoveryInterest()).isEqualByComparingTo("50.10");
        assertThat(sanctioned.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.PRINCIPAL);
    }

    // --- 3. Disbursement ledger debit ---

    @Test
    void disburseLoan_debitsSanctionedAmountFromEeAndLinksLoan() {
        Employee employee = newEmployee("EMP-CPFL2P-4", "cpfl2p4@example.com");
        seedBalance(employee, new BigDecimal("20000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("1000.00"), 1, "test"), null);
        cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("1000.00"), "SANC/2P/002", SANCTION_DATE, 1, 1, null, null, null), null);
        cpfLoanApplicationService.disburseLoan(applied.id(), null);

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry withdrawal = entries.get(entries.size() - 1);
        assertThat(withdrawal.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_WITHDRAWAL);
        assertThat(withdrawal.getEeShareDebit()).isEqualByComparingTo("1000.00");
        assertThat(withdrawal.getTotalDebit()).isEqualByComparingTo("1000.00");
        assertThat(withdrawal.getRunningEeBalance()).isEqualByComparingTo("19000.00");
        assertThat(withdrawal.getLoan()).isNotNull();
        assertThat(withdrawal.getLoan().getId()).isEqualTo(applied.id());
    }

    // --- 4. Two-phase payroll sync: Head 30 clears principal -> transitions to Head 31 -> closes ---

    @Test
    void twoPhasePayrollSync_head30ClearsPrincipal_thenHead31ClearsInterest_thenCloses() {
        Employee employee = newEmployee("EMP-CPFL2P-5", "cpfl2p5@example.com");
        seedBalance(employee, new BigDecimal("20000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("1000.00"), 1, "test"), null);
        CpfLoanApplicationResponse sanctioned = cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("1000.00"), "SANC/2P/003", SANCTION_DATE, 1, 1, null, null, null), null);
        // (1+1) * 1000 * 9.25 / 2400 = 7.71
        assertThat(sanctioned.totalInterestAmount()).isEqualByComparingTo("7.71");
        cpfLoanApplicationService.disburseLoan(applied.id(), null);

        // Batch months are derived from "now" (+1/+2 months), not hardcoded, since disburseLoan() always
        // stamps its LOAN_WITHDRAWAL valueDate as the real wall-clock LocalDate.now() - a hardcoded past
        // month would sort BEFORE that entry and break every "latest ledger entry" lookup below.
        java.time.YearMonth batch1Month = java.time.YearMonth.now().plusMonths(1);
        java.time.YearMonth batch2Month = java.time.YearMonth.now().plusMonths(2);
        String batchFinYear = IncomingFundTransferService.financialYearFor(batch1Month.atEndOfMonth());

        // Batch 1: Head 30 (CPFLOAN_PRIN) fully clears the 1000.00 principal.
        PayrollBatch batch1 = payrollBatchRepository.save(new PayrollBatch("BATCH-CPFL2P-1", batch1Month.getMonthValue(), batch1Month.getYear(), batchFinYear));
        PayrollMonthlyRecord record1 = payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch1, employee,
                employee.getEmployeeCode(), batch1Month.getMonthValue(), batch1Month.getYear(), "01", "MGR", "X", "IDA", 31));
        headItemRepository.save(new PayrollMonthlyHeadItem(record1, 30, new BigDecimal("1000.00")));
        batch1.setStatus(PayrollBatchStatus.HR_FINALIZED);
        cpfLedgerSyncService.disburse(batch1.getId(), null);

        CpfLoanApplicationResponse afterPrincipal = cpfLoanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterPrincipal.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(afterPrincipal.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.INTEREST);
        assertThat(afterPrincipal.status()).isEqualTo(CpfLoanApplicationStatus.DISBURSED);

        List<CpfTrustMemberLedgerEntry> afterPrincipalEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry principalRecoveryEntry = afterPrincipalEntries.get(afterPrincipalEntries.size() - 1);
        assertThat(principalRecoveryEntry.getEeShareCredit()).isEqualByComparingTo("1000.00");
        assertThat(principalRecoveryEntry.getLoanRepayPrincipal()).isEqualByComparingTo("1000.00");
        BigDecimal runningTotalAfterPrincipal = principalRecoveryEntry.getRunningTotalBalance();
        // Principal recovery restores the member's own EE balance (20000 disbursed down to 19000, now back to 20000).
        assertThat(principalRecoveryEntry.getRunningEeBalance()).isEqualByComparingTo("20000.00");
        // The 1000.00 disbursed then fully repaid nets the tracked Refundable Loan balance back to 0.
        assertThat(principalRecoveryEntry.getRunningLoanCpfBalance()).isEqualByComparingTo("0.00");

        // Batch 2 (later month): Head 31 (CPFLOAN_INT) fully clears the 7.71 outstanding interest.
        PayrollBatch batch2 = payrollBatchRepository.save(new PayrollBatch("BATCH-CPFL2P-2", batch2Month.getMonthValue(), batch2Month.getYear(), batchFinYear));
        PayrollMonthlyRecord record2 = payrollMonthlyRecordRepository.save(new PayrollMonthlyRecord(batch2, employee,
                employee.getEmployeeCode(), batch2Month.getMonthValue(), batch2Month.getYear(), "01", "MGR", "X", "IDA", 31));
        headItemRepository.save(new PayrollMonthlyHeadItem(record2, 31, new BigDecimal("7.71")));
        batch2.setStatus(PayrollBatchStatus.HR_FINALIZED);
        cpfLedgerSyncService.disburse(batch2.getId(), null);

        CpfLoanApplicationResponse afterInterest = cpfLoanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(afterInterest.outstandingInterest()).isEqualByComparingTo("0.00");
        assertThat(afterInterest.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.CLOSED);
        assertThat(afterInterest.status()).isEqualTo(CpfLoanApplicationStatus.CLOSED);

        List<CpfTrustMemberLedgerEntry> afterInterestEntries = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry interestRecoveryEntry = afterInterestEntries.get(afterInterestEntries.size() - 1);
        assertThat(interestRecoveryEntry.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_REPAYMENT);
        assertThat(interestRecoveryEntry.getInterestCredit()).isEqualByComparingTo("7.71");
        assertThat(interestRecoveryEntry.getLoanRepayInterest()).isEqualByComparingTo("7.71");
        // Per the Member CPF Passbook's accounting rule, loan interest recovered is credited back into the
        // member's own EE share (20000 + 7.71), unlike principal-phase's own restoration of the same balance.
        assertThat(interestRecoveryEntry.getEeShareCredit()).isEqualByComparingTo("7.71");
        assertThat(interestRecoveryEntry.getRunningEeBalance()).isEqualByComparingTo("20007.71");
        assertThat(interestRecoveryEntry.getRunningTotalBalance()).isEqualByComparingTo(runningTotalAfterPrincipal.add(new BigDecimal("7.71")));
        // Interest was never part of the tracked Refundable Loan principal balance.
        assertThat(interestRecoveryEntry.getRunningLoanCpfBalance()).isEqualByComparingTo("0.00");
    }

    // --- 5. Cash settlement early foreclosure rebate calculation ---

    @Test
    void cashSettlement_earlyForeclosure_appliesInterestRebateAndClosesLoan() {
        Employee employee = newEmployee("EMP-CPFL2P-6", "cpfl2p6@example.com");
        seedBalance(employee, new BigDecimal("30000.00"), BigDecimal.ZERO);

        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("12000.00"), 12, "Home repair"), null);
        CpfLoanApplicationResponse sanctioned = cpfLoanApplicationService.sanctionLoan(applied.id(),
                new CpfLoanSanctionRequest(new BigDecimal("12000.00"), "SANC/2P/004", SANCTION_DATE, 12, 12, null, null, null), null);
        // Original projected interest assuming the full 12-installment tenure: 601.25.
        assertThat(sanctioned.totalInterestAmount()).isEqualByComparingTo("601.25");
        cpfLoanApplicationService.disburseLoan(applied.id(), null);

        // Settling on the same day as disbursement: only the current (1st) month has elapsed, so the
        // recomputed statutory interest on the still-full 12000.00 balance is 12000 * 9.25 / 1200 = 92.50 -
        // far below the 601.25 originally projected for the full tenure, so most of it is rebated.
        CpfLoanSettlementQuoteResponse quote = cpfLoanSettlementService.calculateEarlySettlementQuote(applied.id());
        assertThat(quote.elapsedMonths()).isEqualTo(1);
        assertThat(quote.originalProjectedInterest()).isEqualByComparingTo("601.25");
        assertThat(quote.recomputedStatutoryInterest()).isEqualByComparingTo("92.50");
        assertThat(quote.interestRebateAmount()).isEqualByComparingTo("508.75");
        // netPayoff = 12000 (outstanding principal) + (601.25 - 508.75) = 12092.50
        assertThat(quote.netPayoffAmount()).isEqualByComparingTo("12092.50");

        CpfLoanSettlementRequest settlementRequest = new CpfLoanSettlementRequest(new BigDecimal("12000.00"), new BigDecimal("92.50"),
                CpfLoanSettlementMode.CASH, "CASH-RECEIPT-001", LocalDate.now(), LocalDate.now(), "TRUST-BANK-01", null, "Full early foreclosure");
        CpfLoanSettlementResponse settlement = cpfLoanSettlementService.processCashSettlement(applied.id(), settlementRequest, null);

        assertThat(settlement.receiptVoucherNo()).startsWith("CPFL-RCPT/");
        assertThat(settlement.isEarlyForeclosure()).isTrue();
        assertThat(settlement.interestRebateAmount()).isEqualByComparingTo("508.75");
        assertThat(settlement.totalAmountPaid()).isEqualByComparingTo("12092.50");

        CpfLoanApplicationResponse reloaded = cpfLoanApplicationService.findByEmployee(employee.getId()).stream()
                .filter(l -> l.id().equals(applied.id())).findFirst().orElseThrow();
        assertThat(reloaded.outstandingBalance()).isEqualByComparingTo("0.00");
        assertThat(reloaded.outstandingInterest()).isEqualByComparingTo("0.00");
        assertThat(reloaded.recoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.CLOSED);
        assertThat(reloaded.status()).isEqualTo(CpfLoanApplicationStatus.CLOSED);
        assertThat(reloaded.isPreclosed()).isTrue();
    }

    @Test
    void settlementQuote_onNonDisbursedLoan_rejected() {
        Employee employee = newEmployee("EMP-CPFL2P-7", "cpfl2p7@example.com");
        seedBalance(employee, new BigDecimal("20000.00"), BigDecimal.ZERO);
        CpfLoanApplicationResponse applied = cpfLoanApplicationService.applyLoan(new CpfLoanApplicationRequest(
                employee.getId(), CpfLoanType.REFUNDABLE_LOAN, "HOUSING", new BigDecimal("1000.00"), 1, "test"), null);

        assertThatThrownBy(() -> cpfLoanSettlementService.calculateEarlySettlementQuote(applied.id()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
