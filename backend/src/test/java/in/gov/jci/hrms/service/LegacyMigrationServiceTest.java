package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LegacyMigrationStatusResponse;
import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.dto.ValidateAndPromoteRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.HeadCategory;
import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.LoanRepayment;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.SalaryHeadMaster;
import in.gov.jci.hrms.entity.StagingLegacyLeaveBalance;
import in.gov.jci.hrms.entity.StagingLegacyLoan;
import in.gov.jci.hrms.entity.StagingLegacyLoanTransaction;
import in.gov.jci.hrms.entity.StagingLegacySalaryHead;
import in.gov.jci.hrms.entity.StagingLegacySalaryMonth;
import in.gov.jci.hrms.entity.StagingRowStatus;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.LoanRepaymentRepository;
import in.gov.jci.hrms.repository.LoanTypeRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayslipItemRepository;
import in.gov.jci.hrms.repository.PayslipRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import in.gov.jci.hrms.repository.SalaryHeadMasterRepository;
import in.gov.jci.hrms.repository.StagingLegacyLeaveBalanceRepository;
import in.gov.jci.hrms.repository.StagingLegacyLoanRepository;
import in.gov.jci.hrms.repository.StagingLegacyLoanTransactionRepository;
import in.gov.jci.hrms.repository.StagingLegacySalaryHeadRepository;
import in.gov.jci.hrms.repository.StagingLegacySalaryMonthRepository;
import in.gov.jci.hrms.repository.StagingLegacyServiceBookRepository;
import in.gov.jci.hrms.repository.UnifiedSalaryHeadHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyMigrationServiceTest {

    @Mock
    private StagingLegacyLeaveBalanceRepository leaveBalanceStagingRepository;
    @Mock
    private StagingLegacyLoanRepository loanStagingRepository;
    @Mock
    private StagingLegacyLoanTransactionRepository loanTransactionStagingRepository;
    @Mock
    private StagingLegacySalaryMonthRepository salaryMonthStagingRepository;
    @Mock
    private StagingLegacySalaryHeadRepository salaryHeadStagingRepository;
    @Mock
    private StagingLegacyServiceBookRepository serviceBookStagingRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private LoanTypeRepository loanTypeRepository;
    @Mock
    private EmployeeLoanRepository employeeLoanRepository;
    @Mock
    private LoanRepaymentRepository loanRepaymentRepository;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private PayslipItemRepository payslipItemRepository;
    @Mock
    private SalaryHeadMasterRepository salaryHeadMasterRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private RegionalOfficeRepository regionalOfficeRepository;
    @Mock
    private EmployeeServiceBookRepository employeeServiceBookRepository;
    @Mock
    private UnifiedSalaryHeadHistoryRepository unifiedSalaryHeadHistoryRepository;
    @Mock
    private PayrollComputationService payrollComputationService;

    private LegacyMigrationService legacyMigrationService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        legacyMigrationService = new LegacyMigrationService(
                leaveBalanceStagingRepository, loanStagingRepository, loanTransactionStagingRepository,
                salaryMonthStagingRepository, salaryHeadStagingRepository, serviceBookStagingRepository,
                employeeRepository, leaveTypeRepository, leaveBalanceRepository, loanTypeRepository,
                employeeLoanRepository, loanRepaymentRepository, payrollRunRepository, payslipRepository,
                payslipItemRepository, salaryHeadMasterRepository, departmentRepository, designationRepository,
                regionalOfficeRepository, employeeServiceBookRepository, unifiedSalaryHeadHistoryRepository,
                payrollComputationService);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
    }

    private ValidateAndPromoteRequest promoteRequest(LocalDate cutoffDate) {
        return new ValidateAndPromoteRequest(cutoffDate, "hr.admin");
    }

    // ---- FR-MIG.1: non-blocking row-level validation, partial batch success ----

    @Test
    void validateAndPromote_leaveBalances_partialBatchSucceedsWithDetailedRejectionReasons() {
        LeaveType leaveType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(leaveType, "id", 5L);

        StagingLegacyLeaveBalance validRow = new StagingLegacyLeaveBalance(
                "EMP-001", "EL", new BigDecimal("12.0"), LocalDate.of(2026, 1, 1));
        StagingLegacyLeaveBalance invalidRow = new StagingLegacyLeaveBalance(
                "NO-SUCH-EMP", "EL", new BigDecimal("12.0"), LocalDate.of(2026, 1, 1));

        when(leaveBalanceStagingRepository.findByStatus(StagingRowStatus.PENDING))
                .thenReturn(List.of(validRow, invalidRow));
        when(employeeRepository.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmployeeCode("NO-SUCH-EMP")).thenReturn(Optional.empty());
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 5L, 2026)).thenReturn(Optional.empty());

        legacyMigrationService.validateAndPromote(promoteRequest(LocalDate.of(2026, 8, 1)));

        assertThat(validRow.getStatus()).isEqualTo(StagingRowStatus.PROMOTED);
        assertThat(invalidRow.getStatus()).isEqualTo(StagingRowStatus.REJECTED);
        assertThat(invalidRow.getRejectionReason()).contains("No employee found").contains("NO-SUCH-EMP");
        verify(leaveBalanceRepository, times(1)).save(any());
    }

    // ---- FR-MIG.3 / FR-MIG.4: active vs. closed loan transaction ingestion ----

    @Test
    void validateAndPromote_loanTransactions_onlyMigratedForActiveLoans() {
        LoanType loanType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("12.00"), 48, true, true);
        ReflectionTestUtils.setField(loanType, "id", 7L);

        StagingLegacyLoan activeLoanRow = new StagingLegacyLoan("EMP-001", "CPF_SECURED", "LN-ACT",
                new BigDecimal("12000.00"), new BigDecimal("12.00"), LocalDate.of(2024, 1, 1), 12, LoanStatus.ACTIVE);
        activeLoanRow.setOutstandingPrincipal(new BigDecimal("6000.00"));
        activeLoanRow.setRemainingInstallments(6);
        ReflectionTestUtils.setField(activeLoanRow, "id", 10L);

        StagingLegacyLoan closedLoanRow = new StagingLegacyLoan("EMP-001", "CPF_SECURED", "LN-CLO",
                new BigDecimal("12000.00"), new BigDecimal("12.00"), LocalDate.of(2020, 1, 1), 12, LoanStatus.CLOSED);
        ReflectionTestUtils.setField(closedLoanRow, "id", 11L);

        when(loanStagingRepository.findByStatus(StagingRowStatus.PENDING)).thenReturn(List.of(activeLoanRow, closedLoanRow));
        when(loanStagingRepository.findById(10L)).thenReturn(Optional.of(activeLoanRow));
        when(loanStagingRepository.findById(11L)).thenReturn(Optional.of(closedLoanRow));
        when(employeeRepository.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee));
        when(loanTypeRepository.findByCode(LoanTypeCode.CPF_SECURED)).thenReturn(Optional.of(loanType));

        AtomicReference<EmployeeLoan> savedActiveLoan = new AtomicReference<>();
        when(employeeLoanRepository.save(any(EmployeeLoan.class))).thenAnswer(inv -> {
            EmployeeLoan loan = inv.getArgument(0);
            if ("LN-ACT".equals(loan.getLoanAccountNumber())) {
                ReflectionTestUtils.setField(loan, "id", 100L);
                savedActiveLoan.set(loan);
            } else {
                ReflectionTestUtils.setField(loan, "id", 101L);
            }
            return loan;
        });
        when(employeeLoanRepository.findByLoanAccountNumber("LN-ACT"))
                .thenAnswer(inv -> Optional.ofNullable(savedActiveLoan.get()));
        when(employeeLoanRepository.findByLoanAccountNumber("LN-CLO")).thenReturn(Optional.empty());

        StagingLegacyLoanTransaction txnForActive = new StagingLegacyLoanTransaction(10L, "LN-ACT",
                LocalDate.of(2026, 1, 1), new BigDecimal("900.00"), new BigDecimal("60.00"), new BigDecimal("960.00"));
        StagingLegacyLoanTransaction txnForClosed = new StagingLegacyLoanTransaction(11L, "LN-CLO",
                LocalDate.of(2020, 6, 1), new BigDecimal("900.00"), new BigDecimal("60.00"), new BigDecimal("960.00"));
        when(loanTransactionStagingRepository.findByStatus(StagingRowStatus.PENDING))
                .thenReturn(List.of(txnForActive, txnForClosed));

        legacyMigrationService.validateAndPromote(promoteRequest(LocalDate.of(2026, 8, 1)));

        assertThat(activeLoanRow.getStatus()).isEqualTo(StagingRowStatus.PROMOTED);
        assertThat(closedLoanRow.getStatus()).isEqualTo(StagingRowStatus.PROMOTED);

        assertThat(txnForActive.getStatus()).isEqualTo(StagingRowStatus.PROMOTED);
        assertThat(txnForClosed.getStatus()).isEqualTo(StagingRowStatus.REJECTED);
        assertThat(txnForClosed.getRejectionReason()).contains("ACTIVE loans").contains("CLOSED");

        verify(loanRepaymentRepository, times(1)).save(any(LoanRepayment.class));
    }

    // ---- FR-MIG.5: 5-year cutoff enforcement ----

    @Test
    void validateAndPromote_salaryHistory_rejectsMonthOlderThanFiveYearCutoff() {
        StagingLegacySalaryMonth oldRow = new StagingLegacySalaryMonth("EMP-001", 2019, 1,
                new BigDecimal("1000.00"), new BigDecimal("1100.00"), new BigDecimal("100.00"), new BigDecimal("1000.00"));

        when(salaryMonthStagingRepository.findByStatus(StagingRowStatus.PENDING)).thenReturn(List.of(oldRow));
        when(employeeRepository.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee));

        legacyMigrationService.validateAndPromote(promoteRequest(LocalDate.of(2026, 1, 1)));

        assertThat(oldRow.getStatus()).isEqualTo(StagingRowStatus.REJECTED);
        assertThat(oldRow.getRejectionReason()).contains("migration cutoff");
        verify(payrollRunRepository, never()).save(any());
    }

    // ---- FR-MIG.7: salary head sum-reconciliation failure ----

    @Test
    void validateAndPromote_salaryHistory_rejectsWhenEarningHeadsDoNotReconcileWithGrossEarnings() {
        StagingLegacySalaryMonth row = new StagingLegacySalaryMonth("EMP-001", 2026, 6,
                new BigDecimal("1000.00"), new BigDecimal("1200.00"), new BigDecimal("100.00"), new BigDecimal("1100.00"));
        ReflectionTestUtils.setField(row, "id", 20L);

        StagingLegacySalaryHead specialAllowance = new StagingLegacySalaryHead(20L, "SPECIAL", HeadCategory.EARNING, new BigDecimal("100.00"));

        SalaryHeadMaster specialHeadMaster = new SalaryHeadMaster("SPECIAL", "Special Allowance", HeadType.EARNING, false, true, true);

        when(salaryMonthStagingRepository.findByStatus(StagingRowStatus.PENDING)).thenReturn(List.of(row));
        when(employeeRepository.findByEmployeeCode("EMP-001")).thenReturn(Optional.of(employee));
        when(salaryHeadStagingRepository.findByStagingSalaryMonthId(20L)).thenReturn(List.of(specialAllowance));
        when(salaryHeadMasterRepository.findByCode("SPECIAL")).thenReturn(Optional.of(specialHeadMaster));

        legacyMigrationService.validateAndPromote(promoteRequest(LocalDate.of(2026, 8, 1)));

        // basic (1000.00) + earning heads (100.00) = 1100.00, but gross_earnings was staged as 1200.00
        assertThat(row.getStatus()).isEqualTo(StagingRowStatus.REJECTED);
        assertThat(row.getRejectionReason()).contains("does not reconcile").contains("gross_earnings");
        verify(payslipRepository, never()).save(any());
    }

    // ---- FR-MIG.10: service book chronological timeline ordering ----

    @Test
    void getServiceBookTimeline_preservesChronologicalOrderFromRepository() {
        EmployeeServiceBook joining = mock(EmployeeServiceBook.class);
        when(joining.getId()).thenReturn(1L);
        when(joining.getEventDate()).thenReturn(LocalDate.of(2020, 1, 15));
        when(joining.getEventType()).thenReturn("JOINING");
        when(joining.isMigrated()).thenReturn(true);

        EmployeeServiceBook promotion = mock(EmployeeServiceBook.class);
        when(promotion.getId()).thenReturn(2L);
        when(promotion.getEventDate()).thenReturn(LocalDate.of(2023, 4, 1));
        when(promotion.getEventType()).thenReturn("PROMOTION");
        when(promotion.isMigrated()).thenReturn(true);

        when(employeeRepository.existsById(1L)).thenReturn(true);
        when(employeeServiceBookRepository.findByEmployeeIdOrderByEventDateAscIdAsc(1L))
                .thenReturn(List.of(joining, promotion));

        List<ServiceBookEventResponse> timeline = legacyMigrationService.getServiceBookTimeline(1L);

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).eventType()).isEqualTo("JOINING");
        assertThat(timeline.get(0).eventDate()).isEqualTo(LocalDate.of(2020, 1, 15));
        assertThat(timeline.get(1).eventType()).isEqualTo("PROMOTION");
        assertThat(timeline.get(1).eventDate()).isEqualTo(LocalDate.of(2023, 4, 1));
        verify(employeeServiceBookRepository).findByEmployeeIdOrderByEventDateAscIdAsc(1L);
    }

    // ---- validate-and-promote / status shape sanity ----

    @Test
    void getStatus_returnsAllSixCategories() {
        LegacyMigrationStatusResponse status = legacyMigrationService.getStatus();

        assertThat(status.categories()).extracting("category").containsExactlyInAnyOrder(
                "leave_balances", "loans", "loan_transactions", "salary_months", "salary_heads", "service_book");
    }
}
