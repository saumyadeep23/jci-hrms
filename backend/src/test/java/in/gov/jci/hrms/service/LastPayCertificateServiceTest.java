package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeLoan;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.LoanStatus;
import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;
import in.gov.jci.hrms.entity.MovementLpcRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeLoanRepository;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.MovementLpcRecordRepository;
import in.gov.jci.hrms.service.pdf.LastPayCertificatePdfGenerator;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LastPayCertificateServiceTest {

    @Mock
    private MovementLpcRecordRepository lpcRecordRepository;
    @Mock
    private EmployeeMovementRecordRepository movementRecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock
    private EmployeeLoanRepository employeeLoanRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private PayrollComputationService payrollComputationService;

    private LastPayCertificateService service;
    private Employee employee;
    private EmployeeMovementRecord movement;
    private LeaveType elType;
    private LeaveType hplType;

    @BeforeEach
    void setUp() {
        service = new LastPayCertificateService(lpcRecordRepository, movementRecordRepository, employeeRepository,
                employmentCategoryRepository, employeeLoanRepository, leaveTypeRepository, entitlementBalanceRepository,
                leaveBalanceRepository, payrollComputationService, new LastPayCertificatePdfGenerator(new PdfHeaderFooterHelper()));

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        PayScale payScale = new PayScale(ScaleType.IDA, "E-2", new BigDecimal("40000"), new BigDecimal("80000"),
                new BigDecimal("3.5"), true);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha@example.com", LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setPayScale(payScale);

        RegionalOffice fromOffice = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        RegionalOffice toOffice = new RegionalOffice("RO-B", "Mumbai RO", "Maharashtra", CityClass.X, true);
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/94", LocalDate.of(2026, 3, 1));
        movement = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        movement.setReleaseDate(LocalDate.of(2026, 3, 14));
        movement.setReleaseSession(SessionType.AFTERNOON);
        ReflectionTestUtils.setField(movement, "id", 500L);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);
        hplType = new LeaveType("HPL", "Half Pay Leave", new BigDecimal("20.0"), true, true);
        ReflectionTestUtils.setField(hplType, "id", 3L);

        lenient().when(movementRecordRepository.findById(500L)).thenReturn(Optional.of(movement));
        lenient().when(employmentCategoryRepository.findByEmployeeId(1L))
                .thenReturn(Optional.of(employmentCategoryWithBasicPay(new BigDecimal("45000.00"))));
        lenient().when(payrollComputationService.resolveDaPercentage(any(), any())).thenReturn(new BigDecimal("41.00"));
        lenient().when(payrollComputationService.computeDearnessAllowance(any(), any())).thenReturn(new BigDecimal("18450.00"));
        lenient().when(payrollComputationService.computeHouseRentAllowance(any(), any(), any())).thenReturn(new BigDecimal("10800.00"));
        lenient().when(payrollComputationService.computeEpfEps(any(), any()))
                .thenReturn(new PayrollComputationService.EpfEpsResult(new BigDecimal("7614.00"), new BigDecimal("6500.00"), new BigDecimal("1114.00")));
        lenient().when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        lenient().when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.of(hplType));
        lenient().when(employeeLoanRepository.findByEmployeeId(1L)).thenReturn(List.of());
        lenient().when(lpcRecordRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private EmployeeEmploymentCategory employmentCategoryWithBasicPay(BigDecimal basicPay) {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        category.setRegularBasicPay(basicPay);
        return category;
    }

    @Test
    void getOrCreate_computesRatesFromPayrollComputationServiceAndPersists() {
        LeaveEntitlementBalance elBalance = new LeaveEntitlementBalance(employee, elType, 2026);
        elBalance.setCurrentBalance(new BigDecimal("18.00"));
        when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 2L, 2026)).thenReturn(Optional.of(elBalance));

        LeaveBalance hplBalance = new LeaveBalance(employee, hplType, 2026, new BigDecimal("20.0"));
        hplBalance.setUsedDays(new BigDecimal("5.0"));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 3L, 2026)).thenReturn(Optional.of(hplBalance));

        MovementLpcRecord lpc = service.getOrCreate(500L, 9L);

        assertThat(lpc.getRateOfPayBasic()).isEqualByComparingTo("45000.00");
        assertThat(lpc.getRateOfDa()).isEqualByComparingTo("18450.00");
        assertThat(lpc.getRateOfHra()).isEqualByComparingTo("10800.00");
        assertThat(lpc.getCpfSubscription()).isEqualByComparingTo("7614.00");
        assertThat(lpc.getElBalanceDays()).isEqualTo(18);
        assertThat(lpc.getHplBalanceDays()).isEqualTo(15); // 20 credited - 5 used
        assertThat(lpc.getPayDrawnUptoDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(lpc.getPayDrawnSession()).isEqualTo(SessionType.AFTERNOON);
        assertThat(lpc.getLpcNumber()).isEqualTo("LPC/500/2026");

        verify(payrollComputationService).resolveDaPercentage(ScaleType.IDA, LocalDate.of(2026, 3, 14));
        verify(payrollComputationService).computeHouseRentAllowance(new BigDecimal("45000.00"), new BigDecimal("18450.00"), CityClass.Y);
    }

    @Test
    void getOrCreate_alreadyGenerated_returnsExistingRecordWithoutRecomputing() {
        MovementLpcRecord existing = new MovementLpcRecord(movement, employee, "LPC/500/2026",
                new BigDecimal("45000.00"), new BigDecimal("18450.00"), new BigDecimal("10800.00"), new BigDecimal("7614.00"),
                18, 15, LocalDate.of(2026, 3, 14), SessionType.AFTERNOON);
        when(lpcRecordRepository.findByMovementId(500L)).thenReturn(Optional.of(existing));

        MovementLpcRecord result = service.getOrCreate(500L, 9L);

        assertThat(result).isSameAs(existing);
        verify(payrollComputationService, never()).resolveDaPercentage(any(), any());
        verify(lpcRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCreate_notReleased_throws() {
        movement.setReleaseDate(null);
        movement.setReleaseSession(null);

        assertThatThrownBy(() -> service.getOrCreate(500L, 9L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getOrCreate_outstandingAdvances_summedFromActiveLoansOnly() {
        LoanType cpfSecuredType = new LoanType(LoanTypeCode.CPF_SECURED, "CPF Secured Loan", new BigDecimal("8.5"), 24, true, true);
        LoanType festivalType = new LoanType(LoanTypeCode.FESTIVAL, "Festival Advance", BigDecimal.ZERO, 10, false, true);

        EmployeeLoan activeCpfLoan = new EmployeeLoan(cpfSecuredType, employee, "CPF-001", new BigDecimal("20000.00"),
                new BigDecimal("8.5"), 24, LocalDate.of(2025, 1, 1));
        activeCpfLoan.setOutstandingPrincipal(new BigDecimal("12000.00"));
        activeCpfLoan.setStatus(LoanStatus.ACTIVE);

        EmployeeLoan closedFestivalLoan = new EmployeeLoan(festivalType, employee, "FEST-001", new BigDecimal("5000.00"),
                BigDecimal.ZERO, 10, LocalDate.of(2024, 1, 1));
        closedFestivalLoan.setOutstandingPrincipal(new BigDecimal("5000.00"));
        closedFestivalLoan.setStatus(LoanStatus.CLOSED);

        EmployeeLoan activeFestivalLoan = new EmployeeLoan(festivalType, employee, "FEST-002", new BigDecimal("3000.00"),
                BigDecimal.ZERO, 10, LocalDate.of(2026, 1, 1));
        activeFestivalLoan.setOutstandingPrincipal(new BigDecimal("3000.00"));
        activeFestivalLoan.setStatus(LoanStatus.DISBURSED);

        when(employeeLoanRepository.findByEmployeeId(1L)).thenReturn(List.of(activeCpfLoan, closedFestivalLoan, activeFestivalLoan));

        MovementLpcRecord lpc = service.getOrCreate(500L, 9L);

        assertThat(lpc.getCpfAdvanceBalance()).isEqualByComparingTo("12000.00");
        assertThat(lpc.getFestivalAdvanceBalance()).isEqualByComparingTo("3000.00"); // closed loan excluded
    }
}
