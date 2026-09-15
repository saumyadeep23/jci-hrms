package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCIECCS Lifecycle Engine Phase 5 (spec sections 7-8) - the minimum runtime contract for a running loan
 * inserted directly through pgAdmin by the authorized administrator. Rather than hand-constructing a raw
 * JciEccsLoan/JciEccsLoanProduct/HrmsPayrollCycle graph from scratch, each test creates a real loan via
 * {@link JciEccsLoanService#createLoan} (giving it valid, correctly-wired reference data) and then mutates
 * it directly through the repositories to look exactly like a migrated row - partial prior repayment
 * (outstanding &lt; sanctioned, no JCIECCS recovery history) is the one thing createLoan itself can never
 * produce, and is exactly the shape a real migrated loan has.
 */
@SpringBootTest
@Transactional
class JciEccsMigratedLoanValidationServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsMigratedLoanValidationService validationService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private JciEccsLoanScheduleRepository scheduleRepository;

    private Employee employee;
    private JciEccsMember member;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSML", "JCIECCS Migrated Loan Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Migrated Loan Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JML-1", "Migrated", "Tester", "jml1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        member = new JciEccsMember(employee.getId(), "JECCS-ML-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member = memberRepository.save(member);
    }

    /** Simulates the DBA's own migration: the loan's TRUE original sanctioned amount stays 120000, but
     * outstanding is set to a smaller remaining balance (60000, as if 60000 was already repaid under the
     * old, pre-JCIECCS system) with NO JciEccsRecovery/ledger rows behind that repayment - exactly what a
     * migrated loan looks like, and exactly what would falsely trip
     * JciEccsReconciliationService#reconcileLoan's "original - recovered + reversed = stored" invariant if
     * that check were reused here instead of this dedicated validator. */
    private JciEccsLoan createMigratedLikeLoan() {
        LocalDate disbursementDate = LocalDate.of(2026, 4, 10);
        JciEccsLoanResponse created = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");

        JciEccsLoan loan = loanRepository.findById(created.id()).orElseThrow();
        // Discard createLoan's own full 12-installment schedule and insert only ONE remaining future
        // installment for 60000 - exactly what a DBA migrating a loan already half-repaid under the old
        // system would insert (the true original sanctionedAmount stays 120000, only the REMAINING future
        // installments are ever migrated, never historical/paid ones - spec section 7: "Do not reconstruct
        // historical recovery transactions").
        scheduleRepository.deleteByLoan_IdAndStatusNot(loan.getId(), JciEccsScheduleStatus.PAID);
        scheduleRepository.flush();
        scheduleRepository.save(new JciEccsLoanSchedule(loan, 1, loan.getDisbursementCycle(), new BigDecimal("60000.00"),
                60000, new BigDecimal("500.00"), BigDecimal.ZERO));
        loan.setOutstandingPrincipal(new BigDecimal("60000.00"));
        loanRepository.saveAndFlush(loan);
        return loan;
    }

    @Test
    void validMigratedLoan_partialPriorRepaymentWithNoJciEccsHistory_passesCleanly() {
        JciEccsLoan loan = createMigratedLikeLoan();

        assertThat(validationService.validate(loan.getId())).isEmpty();
    }

    @Test
    void outstandingExceedsSanctioned_flaggedCritical() {
        JciEccsLoan loan = createMigratedLikeLoan();
        loan.setOutstandingPrincipal(new BigDecimal("999999.00"));
        loanRepository.saveAndFlush(loan);

        var findings = validationService.validate(loan.getId());
        assertThat(findings).anyMatch(f -> f.checkType().equals("MIGRATED_LOAN_OUTSTANDING_EXCEEDS_SANCTIONED"));
    }

    // No negative-outstanding test: chk_jcieccs_loan_out (the DB's own CHECK constraint on
    // jcieccs_loan.outstanding_principal) already rejects a negative value before it can ever be persisted
    // - the service's own >= 0 check is defense-in-depth for that same guarantee, not independently
    // testable through a real row.

    @Test
    void outstandingPositiveButNoUsableScheduleRows_flaggedCritical_neverSilentlyRepaired() {
        JciEccsLoan loan = createMigratedLikeLoan();
        scheduleRepository.deleteByLoan_IdAndStatusNot(loan.getId(), JciEccsScheduleStatus.PAID);
        scheduleRepository.flush();

        var findings = validationService.validate(loan.getId());
        assertThat(findings).anyMatch(f -> f.checkType().equals("MIGRATED_LOAN_NO_FUTURE_SCHEDULE"));
    }

    @Test
    void scheduleSumDoesNotMatchOutstanding_flaggedCritical() {
        JciEccsLoan loan = createMigratedLikeLoan();
        // Corrupt the row's remaining-due math (principalDue - principalPaid) so it no longer sums to the
        // loan's own outstanding principal (60000).
        var schedule = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loan.getId());
        var first = schedule.get(0);
        first.setPrincipalPaid(new BigDecimal("500.00"));
        scheduleRepository.saveAndFlush(first);

        var findings = validationService.validate(loan.getId());
        assertThat(findings).anyMatch(f -> f.checkType().equals("MIGRATED_LOAN_SCHEDULE_PRINCIPAL_MISMATCH"));
    }

    @Test
    void memberNotActive_flaggedCritical() {
        JciEccsLoan loan = createMigratedLikeLoan();
        member.setMembershipStatus(JciEccsMembershipStatus.SUSPENDED);
        memberRepository.saveAndFlush(member);

        var findings = validationService.validate(loan.getId());
        assertThat(findings).anyMatch(f -> f.checkType().equals("MIGRATED_LOAN_MEMBER_STATUS"));
    }

    @Test
    void loanStatusNotActive_flaggedCritical() {
        JciEccsLoan loan = createMigratedLikeLoan();
        loan.setStatus(JciEccsLoanStatus.DEFAULTED);
        loanRepository.saveAndFlush(loan);

        var findings = validationService.validate(loan.getId());
        assertThat(findings).anyMatch(f -> f.checkType().equals("MIGRATED_LOAN_STATUS"));
    }
}
