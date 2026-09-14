package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsRestructureRequest;
import in.gov.jci.hrms.dto.JciEccsTopUpRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restructure (no eligibility gate beyond ACTIVE) and Top-Up's explicit 60.00%-repaid boundary (spec
 * section 6: 59.99% rejected, 60.00%/60.01% accepted) - both archive the old loan (status -&gt;
 * RESTRUCTURED, schedule/ledger history untouched) and generate a fresh loan+schedule via the shared
 * regenerate path.
 */
@SpringBootTest
@Transactional
class JciEccsRestructureServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsRestructureService restructureService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSRS", "JCIECCS Restructure Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Restructure Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JRS-1", "Restructure", "Tester", "jrs1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        JciEccsMember member = new JciEccsMember(employee.getId(), "JECCS-RS-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        memberRepository.save(member);
    }

    private JciEccsLoanResponse createTermLoan(BigDecimal sanctionedAmount) {
        LocalDate disbursementDate = LocalDate.of(2033, 2, 10);
        return loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                sanctionedAmount, 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");
    }

    private void setOutstanding(Long loanId, BigDecimal outstanding) {
        JciEccsLoan loan = loanRepository.findById(loanId).orElseThrow();
        loan.setOutstandingPrincipal(outstanding);
        loanRepository.saveAndFlush(loan);
    }

    @Test
    void restructure_archivesTheOldLoan_andGeneratesANewOneAgainstTheOutstandingBalance() {
        JciEccsLoanResponse original = createTermLoan(new BigDecimal("100000"));
        setOutstanding(original.id(), new BigDecimal("70000.00"));

        JciEccsLoanResponse restructured = restructureService.restructure(original.id(),
                new JciEccsRestructureRequest(24, LocalDate.of(2033, 6, 5)), employee.getId());

        assertThat(restructured.parentLoanId()).isEqualTo(original.id());
        assertThat(restructured.sanctionedAmount()).isEqualByComparingTo("70000.00");
        assertThat(restructured.restructuringCount()).isEqualTo(1);

        JciEccsLoan oldLoan = loanRepository.findById(original.id()).orElseThrow();
        assertThat(oldLoan.getStatus()).isEqualTo(JciEccsLoanStatus.RESTRUCTURED);
        // Old loan's own schedule/history is untouched, not deleted.
        assertThat(loanService.getSchedule(original.id())).hasSize(12);
    }

    @Test
    void topUp_59point99PercentRepaid_isRejected() {
        JciEccsLoanResponse loan = createTermLoan(new BigDecimal("100000"));
        setOutstanding(loan.id(), new BigDecimal("40010.00")); // 59990 repaid = 59.99%

        assertThatThrownBy(() -> restructureService.topUp(loan.id(),
                new JciEccsTopUpRequest(new BigDecimal("1000"), 12, LocalDate.of(2033, 6, 5)), employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("60.00%");
    }

    @Test
    void topUp_exactly60PercentRepaid_isAccepted() {
        JciEccsLoanResponse loan = createTermLoan(new BigDecimal("100000"));
        setOutstanding(loan.id(), new BigDecimal("40000.00")); // 60000 repaid = 60.00% exactly

        JciEccsLoanResponse toppedUp = restructureService.topUp(loan.id(),
                new JciEccsTopUpRequest(new BigDecimal("1000"), 12, LocalDate.of(2033, 6, 5)), employee.getId());

        assertThat(toppedUp.sanctionedAmount()).isEqualByComparingTo("41000.00"); // 40000 outstanding + 1000 top-up
        assertThat(toppedUp.topupCount()).isEqualTo(1);
    }

    @Test
    void topUp_60point01PercentRepaid_isAccepted() {
        JciEccsLoanResponse loan = createTermLoan(new BigDecimal("100000"));
        setOutstanding(loan.id(), new BigDecimal("39990.00")); // 60010 repaid = 60.01%

        JciEccsLoanResponse toppedUp = restructureService.topUp(loan.id(),
                new JciEccsTopUpRequest(new BigDecimal("1000"), 12, LocalDate.of(2033, 6, 5)), employee.getId());

        assertThat(toppedUp.sanctionedAmount()).isEqualByComparingTo("40990.00");
    }

    @Test
    void topUp_exceedingProductCeiling_isRejected() {
        JciEccsLoanResponse loan = createTermLoan(new BigDecimal("100000"));
        setOutstanding(loan.id(), new BigDecimal("40000.00")); // eligible (60.00% repaid)

        assertThatThrownBy(() -> restructureService.topUp(loan.id(),
                new JciEccsTopUpRequest(new BigDecimal("470000"), 12, LocalDate.of(2033, 6, 5)), employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ceiling");
    }

    @Test
    void topUp_emergencyLoan_isRejected_topupNotAllowedForThatProduct() {
        LocalDate disbursementDate = LocalDate.of(2033, 2, 10);
        JciEccsLoanResponse emergencyLoan = loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(),
                JciEccsLoanProductCode.EMERGENCY, new BigDecimal("50000"), 10, disbursementDate, disbursementDate, disbursementDate),
                employee.getId(), "tester");

        assertThatThrownBy(() -> restructureService.topUp(emergencyLoan.id(),
                new JciEccsTopUpRequest(new BigDecimal("5000"), 10, LocalDate.of(2033, 6, 5)), employee.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not allowed");
    }
}
