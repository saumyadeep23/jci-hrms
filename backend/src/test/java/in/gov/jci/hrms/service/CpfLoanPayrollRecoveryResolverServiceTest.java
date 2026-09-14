package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3 Part 45/71 Scenario 1 ("Employee 8") - a loan disbursed 20-09-2026 must resolve zero recovery
 * for the September batch (whatever that batch's own DRAFT/CALCULATED/HR_FINALIZED/DISBURSED status is -
 * eligibility is a property of the loan/period pair, not of the batch's processing state) and the first
 * principal instalment for October, under the default NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT policy.
 */
@SpringBootTest
@Transactional
class CpfLoanPayrollRecoveryResolverServiceTest {

    @Autowired private CpfLoanPayrollRecoveryResolverService resolver;
    @Autowired private CpfLoanApplicationRepository loanRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFRECOV", "CPF Recovery Resolver Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Recovery Resolver Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFRECOV-1", "Recovery", "Tester", "cpfrecov1@example.com",
                LocalDate.now().minusYears(3), department, designation));
    }

    private CpfLoanApplication disbursedLoanOn(LocalDate disbursementDate) {
        CpfLoanApplication loan = new CpfLoanApplication("CPFL/TEST/" + System.nanoTime(), employee, CpfLoanType.REFUNDABLE_LOAN,
                "TEMPORARY_HARDSHIP", new BigDecimal("48000.00"));
        loan.setSanctionedAmount(new BigDecimal("48000.00"));
        loan.setTotalInstallments(24);
        loan.setMonthlyRecoveryPrincipal(new BigDecimal("2000.00"));
        loan.setOutstandingBalance(new BigDecimal("48000.00"));
        loan.setTotalInterestAmount(new BigDecimal("2400.00"));
        loan.setOutstandingInterest(new BigDecimal("2400.00"));
        loan.setTotalInterestInstallments(2);
        loan.setMonthlyRecoveryInterest(new BigDecimal("1200.00"));
        loan.setRecoveryPhase(CpfLoanRecoveryPhase.PRINCIPAL);
        loan.setStatus(CpfLoanApplicationStatus.DISBURSED);
        loan.setDisbursedAt(disbursementDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        return loanRepository.saveAndFlush(loan);
    }

    private PayrollBatch batch(int month, int year) {
        return new PayrollBatch("BATCH-RECOV-" + year + "-" + month, month, year, "2026-2027");
    }

    @Test
    void noLoanOnFile_resolvesZero() {
        var amounts = resolver.resolve(employee, batch(10, 2026));
        assertThat(amounts.principal()).isEqualByComparingTo("0");
        assertThat(amounts.interest()).isEqualByComparingTo("0");
    }

    @Test
    void disbursementMonthItself_alwaysResolvesZero_regardlessOfBatchProcessingState() {
        disbursedLoanOn(LocalDate.of(2026, 9, 20));

        var amounts = resolver.resolve(employee, batch(9, 2026));

        assertThat(amounts.principal()).isEqualByComparingTo("0");
        assertThat(amounts.interest()).isEqualByComparingTo("0");
    }

    @Test
    void firstFullCalendarMonthAfterDisbursement_resolvesFirstPrincipalInstallment() {
        disbursedLoanOn(LocalDate.of(2026, 9, 20));

        var amounts = resolver.resolve(employee, batch(10, 2026));

        assertThat(amounts.principal()).isEqualByComparingTo("2000.00");
        assertThat(amounts.interest()).isEqualByComparingTo("0");
    }

    @Test
    void principalNearlyExhausted_capsRecoveryAtRemainingOutstandingBalance_neverOverRecovers() {
        CpfLoanApplication loan = disbursedLoanOn(LocalDate.of(2026, 9, 1));
        loan.setOutstandingBalance(new BigDecimal("500.00")); // less than the usual 2000.00 monthly instalment
        loanRepository.saveAndFlush(loan);

        var amounts = resolver.resolve(employee, batch(10, 2026));

        assertThat(amounts.principal()).isEqualByComparingTo("500.00");
    }

    @Test
    void interestPhase_resolvesInterestOnly_neverPrincipalAgain() {
        CpfLoanApplication loan = disbursedLoanOn(LocalDate.of(2026, 6, 1));
        loan.setOutstandingBalance(BigDecimal.ZERO);
        loan.setRecoveryPhase(CpfLoanRecoveryPhase.INTEREST);
        loanRepository.saveAndFlush(loan);

        var amounts = resolver.resolve(employee, batch(10, 2026));

        assertThat(amounts.principal()).isEqualByComparingTo("0");
        assertThat(amounts.interest()).isEqualByComparingTo("1200.00");
    }

    @Test
    void closedLoan_neverResolvesAnyRecovery() {
        CpfLoanApplication loan = disbursedLoanOn(LocalDate.of(2026, 1, 1));
        loan.setOutstandingBalance(BigDecimal.ZERO);
        loan.setOutstandingInterest(BigDecimal.ZERO);
        loan.setRecoveryPhase(CpfLoanRecoveryPhase.CLOSED);
        loan.setStatus(CpfLoanApplicationStatus.CLOSED);
        loanRepository.saveAndFlush(loan);

        var amounts = resolver.resolve(employee, batch(10, 2026));

        assertThat(amounts.principal()).isEqualByComparingTo("0");
        assertThat(amounts.interest()).isEqualByComparingTo("0");
    }
}
