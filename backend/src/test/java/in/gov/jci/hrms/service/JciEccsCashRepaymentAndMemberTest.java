package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsFinancialPositionResponse;
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
 * Outside-payroll cash repayments (spec section 2: "Transactionally recalculates remaining schedule
 * tenures") and the member financial-position read.
 */
@SpringBootTest
@Transactional
class JciEccsCashRepaymentAndMemberTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsMemberService memberService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;

    private Employee employee;
    private JciEccsLoanResponse loan;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSCR", "JCIECCS Cash Repayment Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Cash Repayment Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JCR-1", "Cash", "Tester", "jcr1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        JciEccsMember member = new JciEccsMember(employee.getId(), "JECCS-CR-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        memberRepository.save(member);

        LocalDate disbursementDate = LocalDate.of(2034, 1, 10);
        loan = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");
    }

    @Test
    void cashRepayment_reducesOutstanding_andShortensTheRemainingScheduleTenure() {
        // 100000 of the 120000 principal paid off in one cash deposit -> 20000 left, at the existing
        // 10000/installment standard rate that's exactly 2 remaining installments (was 12).
        JciEccsLoanResponse updated = loanService.postCashRepayment(loan.id(),
                new JciEccsCashRepaymentRequest(new BigDecimal("100000"), BigDecimal.ZERO, LocalDate.of(2034, 2, 1), "CASH-REF-1", "IDEMP-CASH-1"),
                employee.getId());

        assertThat(updated.outstandingPrincipal()).isEqualByComparingTo("20000.00");

        List<JciEccsLoanScheduleResponse> schedule = loanService.getSchedule(loan.id());
        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).installmentNo()).isEqualTo(1);
        assertThat(schedule.get(1).installmentNo()).isEqualTo(2);
        int totalPrincipal = schedule.stream().mapToInt(JciEccsLoanScheduleResponse::principalDue).sum();
        assertThat(totalPrincipal).isEqualTo(20000);
        assertThat(schedule.get(1).principalOutstanding()).isEqualByComparingTo("0.00");
    }

    @Test
    void cashRepayment_payingOffTheFullOutstandingBalance_closesTheLoan() {
        JciEccsLoanResponse updated = loanService.postCashRepayment(loan.id(),
                new JciEccsCashRepaymentRequest(new BigDecimal("120000"), BigDecimal.ZERO, LocalDate.of(2034, 2, 1), "CASH-REF-2", "IDEMP-CASH-2"),
                employee.getId());

        assertThat(updated.outstandingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(updated.status().name()).isEqualTo("CLOSED");
    }

    @Test
    void financialPosition_showsTheMembersActiveTermLoan() {
        JciEccsFinancialPositionResponse position = memberService.financialPosition(employee.getId());

        assertThat(position.member().employeeId()).isEqualTo(employee.getId());
        assertThat(position.activeTermLoan()).isNotNull();
        assertThat(position.activeTermLoan().loanIssueId()).isEqualTo(loan.loanIssueId());
        assertThat(position.activeEmergencyLoan()).isNull();
    }
}
