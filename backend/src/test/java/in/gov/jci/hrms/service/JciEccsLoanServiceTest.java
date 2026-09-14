package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsLoanScheduleResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
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
 * The JCIECCS loan lifecycle (apply-and-disburse in one step, per spec section 5) and its amortization
 * schedule - proving the Emergency Loan's 2x-interest-on-installment-1 rule, the Term Loan's
 * installment-1-starts-in-the-disbursement-cycle rule, and the ₹10-multiple/final-installment-balances-
 * to-zero arithmetic rules (spec section 6).
 */
@SpringBootTest
@Transactional
class JciEccsLoanServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSLN", "JCIECCS Loan Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Loan Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JEL-1", "Loan", "Tester", "jel1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        JciEccsMember member = new JciEccsMember(employee.getId(), "JECCS-LN-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        memberRepository.save(member);
    }

    @Test
    void termLoan_installmentOne_startsInTheDisbursementCycle_andPrincipalIsAMultipleOfTen() {
        LocalDate disbursementDate = LocalDate.of(2026, 4, 10); // within the 26-25 window -> cycle 2026-04
        JciEccsLoanCreateRequest request = new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate);

        JciEccsLoanResponse loan = loanService.createLoan(request, employee.getId(), "tester");

        assertThat(loan.loanIssueId()).startsWith("TE-");
        assertThat(loan.monthlyPrincipalInstallment()).isEqualTo(10000);
        assertThat(loan.monthlyPrincipalInstallment() % 10).isZero();

        List<JciEccsLoanScheduleResponse> schedule = loanService.getSchedule(loan.id());
        assertThat(schedule).hasSize(12);
        assertThat(schedule.get(0).cycleCode()).isEqualTo(loan.disbursementCycleCode()); // Term: installment 1 = disbursement cycle

        int totalPrincipal = schedule.stream().mapToInt(JciEccsLoanScheduleResponse::principalDue).sum();
        assertThat(totalPrincipal).isEqualTo(120000);
        assertThat(schedule.get(11).principalOutstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void emergencyLoan_installmentOne_isInTheCycleAfterDisbursement_withDoubleInterest() {
        LocalDate disbursementDate = LocalDate.of(2026, 4, 10); // disbursement cycle 2026-04 -> repayment starts 2026-05
        JciEccsLoanCreateRequest request = new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.EMERGENCY,
                new BigDecimal("12000"), 10, disbursementDate, disbursementDate, disbursementDate);

        JciEccsLoanResponse loan = loanService.createLoan(request, employee.getId(), "tester");

        assertThat(loan.loanIssueId()).startsWith("EM-");
        assertThat(loan.disbursementCycleCode()).isEqualTo("2026-04");

        List<JciEccsLoanScheduleResponse> schedule = loanService.getSchedule(loan.id());
        assertThat(schedule).hasSize(10);
        assertThat(schedule.get(0).cycleCode()).isEqualTo("2026-05"); // Emergency: installment 1 = disbursement cycle + 1
        assertThat(schedule.get(1).cycleCode()).isEqualTo("2026-06");

        // Standard monthly interest at installment 2's opening principal (10800) @ 10% p.a. = 90.00;
        // installment 1's opening principal (12000) would normally also be 100.00, but must be exactly 2x = 200.00.
        assertThat(schedule.get(0).interestDue()).isEqualByComparingTo("200.00");
        assertThat(schedule.get(1).interestDue()).isEqualByComparingTo("90.00");

        int totalPrincipal = schedule.stream().mapToInt(JciEccsLoanScheduleResponse::principalDue).sum();
        assertThat(totalPrincipal).isEqualTo(12000);
        assertThat(schedule.get(9).principalOutstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void finalInstallment_cleansUpRemainingOddPrincipal_toExactlyZero() {
        LocalDate disbursementDate = LocalDate.of(2026, 6, 5);
        // 100000 / 3 = 33333.33 -> nearest 10 = 33330 standard installment; 2*33330=66660; remainder for
        // installment 3 = 100000-66660 = 33340 (not a multiple of 10 - the final-installment cleanup).
        JciEccsLoanCreateRequest request = new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("100000"), 3, disbursementDate, disbursementDate, disbursementDate);

        JciEccsLoanResponse loan = loanService.createLoan(request, employee.getId(), "tester");
        List<JciEccsLoanScheduleResponse> schedule = loanService.getSchedule(loan.id());

        assertThat(schedule.get(0).principalDue()).isEqualTo(33330);
        assertThat(schedule.get(1).principalDue()).isEqualTo(33330);
        assertThat(schedule.get(2).principalDue()).isEqualTo(33340);
        assertThat(schedule.get(2).principalOutstanding()).isEqualByComparingTo("0.00");
    }
}
