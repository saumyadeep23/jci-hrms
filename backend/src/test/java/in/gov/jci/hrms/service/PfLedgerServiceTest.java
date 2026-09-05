package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfBalanceLedgerRequest;
import in.gov.jci.hrms.dto.CpfBalanceLedgerResponse;
import in.gov.jci.hrms.dto.NonRefundableWithdrawalRequest;
import in.gov.jci.hrms.dto.PfDiversionResponse;
import in.gov.jci.hrms.dto.RefundableLoanDiversionRequest;
import in.gov.jci.hrms.entity.CpfBalanceLedger;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.DiversionStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.PfDiversion;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfBalanceLedgerRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PfDiversionRepository;
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
class PfLedgerServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long LOAN_ID = 2L;

    @Mock
    private CpfBalanceLedgerRepository cpfBalanceLedgerRepository;
    @Mock
    private PfDiversionRepository pfDiversionRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeLoanRepository employeeLoanRepository;

    private PfLedgerService pfLedgerService;
    private Employee employee;
    private EmployeeLoan loan;

    @BeforeEach
    void setUp() {
        pfLedgerService = new PfLedgerService(cpfBalanceLedgerRepository, pfDiversionRepository, employeeRepository,
                employeeLoanRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        LoanType loanType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("8.00"), 48, true, true);
        loan = new EmployeeLoan(loanType, employee, "LN-2026-001", new BigDecimal("5000.00"),
                new BigDecimal("8.00"), 24, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(loan, "id", LOAN_ID);
    }

    private CpfBalanceLedger ledgerWith(BigDecimal emp, BigDecimal er, BigDecimal vpf) {
        return new CpfBalanceLedger(employee, emp, er, vpf);
    }

    // ---- createLedger ----

    @Test
    void createLedger_savesAndReturnsResponse() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(cpfBalanceLedgerRepository.saveAndFlush(any(CpfBalanceLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CpfBalanceLedgerResponse response = pfLedgerService.createLedger(
                new CpfBalanceLedgerRequest(EMPLOYEE_ID, new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("2000.00")));

        assertThat(response.employeeFundBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    void createLedger_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pfLedgerService.createLedger(
                new CpfBalanceLedgerRequest(EMPLOYEE_ID, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    // ---- createRefundableLoanDiversion (employee bucket only) ----

    @Test
    void createRefundableLoanDiversion_withSufficientBalance_deductsOnlyFromEmployeeBucket() {
        CpfBalanceLedger ledger = ledgerWith(new BigDecimal("10000.00"), new BigDecimal("8000.00"), new BigDecimal("2000.00"));
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(ledger));
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(pfDiversionRepository.save(any(PfDiversion.class))).thenAnswer(inv -> inv.getArgument(0));

        PfDiversionResponse response = pfLedgerService.createRefundableLoanDiversion(
                new RefundableLoanDiversionRequest(EMPLOYEE_ID, new BigDecimal("5000.00"), LOAN_ID, LocalDate.of(2026, 1, 1)));

        assertThat(response.empBucketAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.erBucketAmount()).isEqualByComparingTo("0");
        assertThat(response.vpfBucketAmount()).isEqualByComparingTo("0");
        assertThat(ledger.getEmployeeFundBalance()).isEqualByComparingTo("5000.00");
        assertThat(ledger.getEmployerFundBalance()).isEqualByComparingTo("8000.00");
    }

    @Test
    void createRefundableLoanDiversion_withInsufficientEmployeeBalance_throwsBusinessRuleViolationException() {
        CpfBalanceLedger ledger = ledgerWith(new BigDecimal("1000.00"), new BigDecimal("8000.00"), new BigDecimal("2000.00"));
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(ledger));
        when(employeeLoanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> pfLedgerService.createRefundableLoanDiversion(
                new RefundableLoanDiversionRequest(EMPLOYEE_ID, new BigDecimal("5000.00"), LOAN_ID, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createRefundableLoanDiversion_whenNoLedgerExists_throwsMasterDataNotFoundException() {
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pfLedgerService.createRefundableLoanDiversion(
                new RefundableLoanDiversionRequest(EMPLOYEE_ID, new BigDecimal("5000.00"), LOAN_ID, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    // ---- createNonRefundableWithdrawal (distribution across 3 buckets) ----

    @Test
    void createNonRefundableWithdrawal_deductsFromAllThreeBucketsIndependently() {
        CpfBalanceLedger ledger = ledgerWith(new BigDecimal("10000.00"), new BigDecimal("8000.00"), new BigDecimal("2000.00"));
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(ledger));
        when(pfDiversionRepository.save(any(PfDiversion.class))).thenAnswer(inv -> inv.getArgument(0));

        PfDiversionResponse response = pfLedgerService.createNonRefundableWithdrawal(
                new NonRefundableWithdrawalRequest(EMPLOYEE_ID, new BigDecimal("3000.00"), new BigDecimal("2000.00"),
                        new BigDecimal("500.00"), LocalDate.of(2026, 1, 1)));

        assertThat(response.totalAmount()).isEqualByComparingTo("5500.00");
        assertThat(ledger.getEmployeeFundBalance()).isEqualByComparingTo("7000.00");
        assertThat(ledger.getEmployerFundBalance()).isEqualByComparingTo("6000.00");
        assertThat(ledger.getVpfBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void createNonRefundableWithdrawal_whenOneBucketInsufficient_throwsBusinessRuleViolationException_andLeavesLedgerUntouched() {
        CpfBalanceLedger ledger = ledgerWith(new BigDecimal("10000.00"), new BigDecimal("100.00"), new BigDecimal("2000.00"));
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(ledger));

        assertThatThrownBy(() -> pfLedgerService.createNonRefundableWithdrawal(
                new NonRefundableWithdrawalRequest(EMPLOYEE_ID, new BigDecimal("3000.00"), new BigDecimal("2000.00"),
                        new BigDecimal("500.00"), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(ledger.getEmployeeFundBalance()).isEqualByComparingTo("10000.00");
    }

    // ---- settle ----

    @Test
    void settle_movesActiveToSettled() {
        PfDiversion diversion = new PfDiversion(employee, in.gov.jci.hrms.entity.DiversionType.REFUNDABLE_LOAN,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, loan, LocalDate.now());
        ReflectionTestUtils.setField(diversion, "id", 10L);
        when(pfDiversionRepository.findById(10L)).thenReturn(Optional.of(diversion));

        PfDiversionResponse response = pfLedgerService.settle(10L);

        assertThat(response.status()).isEqualTo(DiversionStatus.SETTLED);
    }

    @Test
    void settle_whenAlreadySettled_throwsBusinessRuleViolationException() {
        PfDiversion diversion = new PfDiversion(employee, in.gov.jci.hrms.entity.DiversionType.REFUNDABLE_LOAN,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, loan, LocalDate.now());
        diversion.setStatus(DiversionStatus.SETTLED);
        ReflectionTestUtils.setField(diversion, "id", 10L);
        when(pfDiversionRepository.findById(10L)).thenReturn(Optional.of(diversion));

        assertThatThrownBy(() -> pfLedgerService.settle(10L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
