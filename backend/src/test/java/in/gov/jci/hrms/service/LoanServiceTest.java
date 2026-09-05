package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeLoanRequest;
import in.gov.jci.hrms.dto.EmployeeLoanResponse;
import in.gov.jci.hrms.dto.LoanForecloseRequest;
import in.gov.jci.hrms.dto.LoanRepaymentResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.LoanTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Long LOAN_TYPE_ID = 1L;
    private static final Long EMPLOYEE_ID = 2L;
    private static final Long LOAN_ID = 3L;

    @Mock
    private EmployeeLoanRepository employeeLoanRepository;
    @Mock
    private LoanRepaymentRepository loanRepaymentRepository;
    @Mock
    private LoanTypeRepository loanTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private LoanService loanService;
    private LoanType loanType;
    private Employee employee;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(employeeLoanRepository, loanRepaymentRepository, loanTypeRepository, employeeRepository);

        loanType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("12.00"), 48, true, true);
        ReflectionTestUtils.setField(loanType, "id", LOAN_TYPE_ID);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private EmployeeLoanRequest validRequest() {
        return new EmployeeLoanRequest(LOAN_TYPE_ID, EMPLOYEE_ID, "LN-2026-001", new BigDecimal("12000.00"), 12,
                LocalDate.of(2026, 1, 1));
    }

    private EmployeeLoan loanFrom(Long id, EmployeeLoanRequest request) {
        EmployeeLoan loan = new EmployeeLoan(loanType, employee, request.loanAccountNumber(), request.principalAmount(),
                loanType.getInterestRateAnnual(), request.totalInstallments(), request.sanctionDate());
        ReflectionTestUtils.setField(loan, "id", id);
        return loan;
    }

    // ---- computeReducingBalanceInterest (FR-LOAN.3) ----

    @Test
    void computeReducingBalanceInterest_appliesAnnualRateAsMonthlyPeriodicRate() {
        // 12000 outstanding at 12% annual -> 1% monthly -> 120.00
        BigDecimal interest = loanService.computeReducingBalanceInterest(new BigDecimal("12000.00"), new BigDecimal("12.00"));

        assertThat(interest).isEqualByComparingTo("120.00");
    }

    @Test
    void computeReducingBalanceInterest_scalesWithOutstandingPrincipal_notOriginalPrincipal() {
        // Same rate, smaller outstanding balance -> proportionally smaller interest.
        BigDecimal interest = loanService.computeReducingBalanceInterest(new BigDecimal("6000.00"), new BigDecimal("12.00"));

        assertThat(interest).isEqualByComparingTo("60.00");
    }

    // ---- sanction ----

    @Test
    void sanction_createsLoanWithFullPrincipalOutstandingAndSanctionedStatus() {
        when(loanTypeRepository.findById(LOAN_TYPE_ID)).thenReturn(Optional.of(loanType));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employeeLoanRepository.saveAndFlush(any(EmployeeLoan.class))).thenAnswer(inv -> {
            EmployeeLoan saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", LOAN_ID);
            return saved;
        });

        EmployeeLoanResponse response = loanService.sanction(validRequest());

        assertThat(response.status()).isEqualTo(LoanStatus.SANCTIONED);
        assertThat(response.outstandingPrincipal()).isEqualByComparingTo("12000.00");
        assertThat(response.remainingInstallments()).isEqualTo(12);
        assertThat(response.interestRate()).isEqualByComparingTo("12.00");
    }

    @Test
    void sanction_whenInstallmentsExceedLoanTypeMax_throwsBusinessRuleViolationException() {
        when(loanTypeRepository.findById(LOAN_TYPE_ID)).thenReturn(Optional.of(loanType));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        EmployeeLoanRequest tooMany = new EmployeeLoanRequest(LOAN_TYPE_ID, EMPLOYEE_ID, "LN-2026-002",
                new BigDecimal("12000.00"), 100, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> loanService.sanction(tooMany))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void sanction_whenLoanTypeMissing_throwsMasterDataNotFoundException() {
        when(loanTypeRepository.findById(LOAN_TYPE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.sanction(validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void sanction_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(loanTypeRepository.findById(LOAN_TYPE_ID)).thenReturn(Optional.of(loanType));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.sanction(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void sanction_whenAccountNumberAlreadyInUse_throwsMasterDataConflictException() {
        when(loanTypeRepository.findById(LOAN_TYPE_ID)).thenReturn(Optional.of(loanType));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employeeLoanRepository.saveAndFlush(any(EmployeeLoan.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> loanService.sanction(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    // ---- disburse ----

    @Test
    void disburse_movesSanctionedToDisbursed() {
        EmployeeLoan loan = loanFrom(LOAN_ID, validRequest());
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        EmployeeLoanResponse response = loanService.disburse(LOAN_ID);

        assertThat(response.status()).isEqualTo(LoanStatus.DISBURSED);
    }

    @Test
    void disburse_whenNotSanctioned_throwsBusinessRuleViolationException() {
        EmployeeLoan loan = loanFrom(LOAN_ID, validRequest());
        loan.setStatus(LoanStatus.ACTIVE);
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.disburse(LOAN_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- foreclose ----

    @Test
    void foreclose_paysOffFinalInterestAndFullOutstandingPrincipal() {
        EmployeeLoan loan = loanFrom(LOAN_ID, validRequest());
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingPrincipal(new BigDecimal("6000.00"));
        loan.setRemainingInstallments(6);
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        LoanRepaymentResponse response = loanService.foreclose(LOAN_ID,
                new LoanForecloseRequest(LocalDate.of(2026, 6, 1), "FORECLOSE-REF-1"));

        // interest = 6000 * 12% / 12 = 60.00; amount = 6060.00
        assertThat(response.interestComponent()).isEqualByComparingTo("60.00");
        assertThat(response.principalComponent()).isEqualByComparingTo("6000.00");
        assertThat(response.amount()).isEqualByComparingTo("6060.00");
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("0");
        assertThat(loan.getRemainingInstallments()).isEqualTo(0);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.FORECLOSED);
    }

    @Test
    void foreclose_whenAlreadyClosed_throwsBusinessRuleViolationException() {
        EmployeeLoan loan = loanFrom(LOAN_ID, validRequest());
        loan.setStatus(LoanStatus.CLOSED);
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.foreclose(LOAN_ID,
                new LoanForecloseRequest(LocalDate.of(2026, 6, 1), null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(employeeLoanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
