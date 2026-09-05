package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LoanRepaymentRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.RepaymentSource;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentServiceTest {

    private static final Long LOAN_ID = 1L;

    @Mock
    private LoanRepaymentRepository loanRepaymentRepository;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private LoanService loanService;

    private LoanRepaymentService loanRepaymentService;
    private EmployeeLoan loan;

    @BeforeEach
    void setUp() {
        loanRepaymentService = new LoanRepaymentService(loanRepaymentRepository, payrollRunRepository, loanService);

        LoanType loanType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("12.00"), 48, true, true);
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);

        loan = new EmployeeLoan(loanType, employee, "LN-2026-001", new BigDecimal("12000.00"),
                new BigDecimal("12.00"), 12, LocalDate.of(2026, 1, 1));
        loan.setStatus(LoanStatus.ACTIVE);
        ReflectionTestUtils.setField(loan, "id", LOAN_ID);
    }

    private LoanRepaymentRequest requestOf(BigDecimal amount) {
        return new LoanRepaymentRequest(RepaymentSource.PAYROLL_DEDUCTION, amount, LocalDate.of(2026, 2, 1), null, null);
    }

    @Test
    void recordRepayment_splitsIntoInterestAndPrincipalAndReducesOutstanding() {
        // outstanding=12000, rate=12% -> interest = 120.00; amount=1120.00 -> principal=1000.00
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);
        when(loanService.computeReducingBalanceInterest(new BigDecimal("12000.00"), new BigDecimal("12.00")))
                .thenReturn(new BigDecimal("120.00"));

        LoanRepaymentResponse response = loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("1120.00")));

        assertThat(response.interestComponent()).isEqualByComparingTo("120.00");
        assertThat(response.principalComponent()).isEqualByComparingTo("1000.00");
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("11000.00");
        assertThat(loan.getRemainingInstallments()).isEqualTo(11);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void recordRepayment_onFinalInstallment_closesLoan() {
        loan.setOutstandingPrincipal(new BigDecimal("1000.00"));
        loan.setRemainingInstallments(1);
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);
        when(loanService.computeReducingBalanceInterest(new BigDecimal("1000.00"), new BigDecimal("12.00")))
                .thenReturn(new BigDecimal("10.00"));

        LoanRepaymentResponse response = loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("1010.00")));

        assertThat(response.principalComponent()).isEqualByComparingTo("1000.00");
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("0");
        assertThat(loan.getRemainingInstallments()).isEqualTo(0);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }

    @Test
    void recordRepayment_transitionsDisbursedToActiveOnFirstRepayment() {
        loan.setStatus(LoanStatus.DISBURSED);
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);
        when(loanService.computeReducingBalanceInterest(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new BigDecimal("120.00"));

        loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("1120.00")));

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void recordRepayment_whenAmountLessThanAccruedInterest_throwsBusinessRuleViolationException() {
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);
        when(loanService.computeReducingBalanceInterest(new BigDecimal("12000.00"), new BigDecimal("12.00")))
                .thenReturn(new BigDecimal("120.00"));

        assertThatThrownBy(() -> loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("50.00"))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void recordRepayment_whenLoanNotDisbursedOrActive_throwsBusinessRuleViolationException() {
        loan.setStatus(LoanStatus.SANCTIONED);
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);

        assertThatThrownBy(() -> loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("1120.00"))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void recordRepayment_whenNoRemainingInstallments_throwsBusinessRuleViolationException() {
        loan.setRemainingInstallments(0);
        when(loanService.findOrThrow(LOAN_ID)).thenReturn(loan);

        assertThatThrownBy(() -> loanRepaymentService.recordRepayment(LOAN_ID, requestOf(new BigDecimal("1120.00"))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
