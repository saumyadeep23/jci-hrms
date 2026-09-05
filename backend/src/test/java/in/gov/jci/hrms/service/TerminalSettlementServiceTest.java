package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TerminalSettlementBeneficiaryRequest;
import in.gov.jci.hrms.dto.TerminalSettlementCalculation;
import in.gov.jci.hrms.entity.BeneficiaryType;
import in.gov.jci.hrms.entity.CpfBalanceLedger;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementBeneficiary;
import in.gov.jci.hrms.entity.TerminalSettlementStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfBalanceLedgerRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceItemRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.TerminalSettlementBeneficiaryRepository;
import in.gov.jci.hrms.repository.TerminalSettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalSettlementServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private RegularPayFixationRepository regularPayFixationRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock
    private DaRateHistoryRepository daRateHistoryRepository;
    @Mock
    private PayrollComputationService payrollComputationService;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveEntitlementBalanceRepository leaveEntitlementBalanceRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private CpfBalanceLedgerRepository cpfBalanceLedgerRepository;
    @Mock
    private ExitClearanceItemRepository exitClearanceItemRepository;
    @Mock
    private ExitClearanceRequestRepository exitClearanceRequestRepository;
    @Mock
    private EmployeeBankAccountRepository employeeBankAccountRepository;
    @Mock
    private EmployeeNomineeRepository employeeNomineeRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private TerminalSettlementRepository terminalSettlementRepository;
    @Mock
    private TerminalSettlementBeneficiaryRepository beneficiaryRepository;

    private TerminalSettlementService service;

    private Employee employee;
    private LeaveType elType;
    private LeaveType hplType;

    private static final Long EMPLOYEE_ID = 500L;
    private static final Long EL_TYPE_ID = 2L;
    private static final Long HPL_TYPE_ID = 3L;

    @BeforeEach
    void setUp() {
        service = new TerminalSettlementService(employeeRepository, regularPayFixationRepository, employmentCategoryRepository,
                daRateHistoryRepository, payrollComputationService, leaveTypeRepository, leaveEntitlementBalanceRepository,
                leaveBalanceRepository, cpfBalanceLedgerRepository, exitClearanceItemRepository, exitClearanceRequestRepository,
                employeeBankAccountRepository, employeeNomineeRepository, leaveLedgerEntryRepository,
                terminalSettlementRepository, beneficiaryRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        employee = new Employee("2801", "Test", "Employee", "test@example.com", LocalDate.of(2000, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", EL_TYPE_ID);
        hplType = new LeaveType("HPL", "Half Pay Leave", new BigDecimal("20.0"), false, true);
        ReflectionTestUtils.setField(hplType, "id", HPL_TYPE_ID);

        lenient().when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        lenient().when(regularPayFixationRepository.findApplicableForSettlement(eq(EMPLOYEE_ID), any())).thenReturn(List.of());
        lenient().when(employmentCategoryRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());
        lenient().when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        lenient().when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.of(hplType));
        lenient().when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.empty());
        lenient().when(leaveEntitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(EL_TYPE_ID), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(HPL_TYPE_ID), anyInt()))
                .thenReturn(Optional.empty());
        // Mirrors PayrollComputationService.computeDearnessAllowance's own round(basic * pct / 100) formula,
        // decoupling this unit test from that service's internals while keeping the arithmetic real.
        lenient().when(payrollComputationService.computeDearnessAllowance(any(), any()))
                .thenAnswer(inv -> {
                    BigDecimal basic = inv.getArgument(0);
                    BigDecimal pct = inv.getArgument(1);
                    return basic.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                });
    }

    private void stubEmploymentCategoryBasicPay(BigDecimal basicPay) {
        in.gov.jci.hrms.entity.EmployeeEmploymentCategory category =
                new in.gov.jci.hrms.entity.EmployeeEmploymentCategory(employee, in.gov.jci.hrms.entity.EmploymentCategory.REGULAR);
        category.setRegularBasicPay(basicPay);
        when(employmentCategoryRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(category));
    }

    private void stubDaRate(BigDecimal percentage) {
        DaRateHistory rate = new DaRateHistory(ScaleType.IDA, LocalDate.of(2020, 1, 1), percentage, true);
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(eq(ScaleType.IDA), any()))
                .thenReturn(Optional.of(rate));
    }

    // -------------------------------------------------------------------
    // DoPT Rule 39: EL < 300 with HPL shortfall
    // -------------------------------------------------------------------
    @Test
    void calculate_elBelow300_topsUpFromHplShortfall() {
        stubEmploymentCategoryBasicPay(new BigDecimal("50000.00"));
        stubDaRate(new BigDecimal("20.00"));

        LeaveEntitlementBalance elBalance = new LeaveEntitlementBalance(employee, elType, 2026);
        elBalance.setEncashableAvailable(new BigDecimal("250.00"));
        when(leaveEntitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, EL_TYPE_ID, 2026))
                .thenReturn(Optional.of(elBalance));

        LeaveBalance hplBalance = new LeaveBalance(employee, hplType, 2026, new BigDecimal("100.0"));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_TYPE_ID, 2026))
                .thenReturn(Optional.of(hplBalance));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 6, 15), null, null);

        assertThat(c.elBalanceAtRetirement()).isEqualByComparingTo("250.00");
        assertThat(c.hplBalanceAtRetirement()).isEqualByComparingTo("100.0");
        assertThat(c.elDaysEncashed()).isEqualByComparingTo("250.00");
        assertThat(c.hplDaysEncashed()).isEqualByComparingTo("50.00");
        // monthlyEmoluments = 50000 + 10000 = 60000; elCash = 60000/30 * 250 = 500000.00
        assertThat(c.leaveEncashmentElAmount()).isEqualByComparingTo("500000.00");
        // half-emoluments = 25000 + 5000 = 30000; hplCash = 30000/30 * 50 = 50000.00
        assertThat(c.leaveEncashmentHplAmount()).isEqualByComparingTo("50000.00");
        assertThat(c.totalLeaveEncashment()).isEqualByComparingTo("550000.00");
    }

    @Test
    void calculate_elAtOrAbove300_noHplTopUp() {
        stubEmploymentCategoryBasicPay(new BigDecimal("50000.00"));
        stubDaRate(new BigDecimal("20.00"));

        LeaveEntitlementBalance elBalance = new LeaveEntitlementBalance(employee, elType, 2026);
        elBalance.setEncashableAvailable(new BigDecimal("310.00"));
        when(leaveEntitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, EL_TYPE_ID, 2026))
                .thenReturn(Optional.of(elBalance));

        LeaveBalance hplBalance = new LeaveBalance(employee, hplType, 2026, new BigDecimal("100.0"));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_TYPE_ID, 2026))
                .thenReturn(Optional.of(hplBalance));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 6, 15), null, null);

        assertThat(c.elDaysEncashed()).isEqualByComparingTo("300.0");
        assertThat(c.hplDaysEncashed()).isEqualByComparingTo("0.0");
        assertThat(c.leaveEncashmentHplAmount()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------
    // Death gratuity minimum slabs for short tenures
    // -------------------------------------------------------------------
    @Test
    void calculate_deathGratuity_underOneYearService_twiceEmoluments() {
        stubEmploymentCategoryBasicPay(new BigDecimal("40000.00"));
        stubDaRate(new BigDecimal("10.00"));
        // date_of_joining is fixed to 2000-01-01 in setUp's employee fixture; override via a fresh employee for a short-tenure death case.
        Employee shortTenure = newEmployeeWithJoining(LocalDate.of(2026, 1, 1));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(shortTenure));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.DECEASED, LocalDate.of(2026, 6, 1), null, null);

        // monthlyEmoluments = 40000 + 4000 = 44000; QS = 5 months < 1 year -> 2x
        assertThat(c.isDeathGratuity()).isTrue();
        assertThat(c.gratuityAmount()).isEqualByComparingTo("88000.00");
    }

    @Test
    void calculate_deathGratuity_between1And5Years_sixTimesEmoluments() {
        stubEmploymentCategoryBasicPay(new BigDecimal("40000.00"));
        stubDaRate(new BigDecimal("10.00"));
        Employee threeYears = newEmployeeWithJoining(LocalDate.of(2023, 1, 1));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(threeYears));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.DECEASED, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.gratuityAmount()).isEqualByComparingTo("264000.00"); // 44000 * 6
    }

    // -------------------------------------------------------------------
    // Gratuity statutory caps
    // -------------------------------------------------------------------
    @Test
    void calculate_retirementGratuity_capsAt20Lakh_whenDaRateBelow50() {
        stubEmploymentCategoryBasicPay(new BigDecimal("500000.00"));
        stubDaRate(new BigDecimal("20.00"));
        Employee longService = newEmployeeWithJoining(LocalDate.of(1986, 1, 1));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(longService));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.gratuityAmount()).isEqualByComparingTo("2000000.00");
    }

    @Test
    void calculate_retirementGratuity_capsAt25Lakh_whenDaRateAtOrAbove50() {
        stubEmploymentCategoryBasicPay(new BigDecimal("500000.00"));
        stubDaRate(new BigDecimal("50.00"));
        Employee longService = newEmployeeWithJoining(LocalDate.of(1986, 1, 1));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(longService));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.gratuityAmount()).isEqualByComparingTo("2500000.00");
    }

    @Test
    void calculate_retirementGratuity_belowCap_usesFormula() {
        stubEmploymentCategoryBasicPay(new BigDecimal("26000.00"));
        stubDaRate(new BigDecimal("10.00"));
        Employee tenYears = newEmployeeWithJoining(LocalDate.of(2016, 1, 1));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(tenYears));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 1, 1), null, null);

        // monthlyEmoluments = 26000 + 2600 = 28600; (28600/26)*15*10 = 1100*15*10 = 165000.00
        assertThat(c.gratuityAmount()).isEqualByComparingTo("165000.00");
    }

    // -------------------------------------------------------------------
    // CPF ledger balance sum
    // -------------------------------------------------------------------
    @Test
    void calculate_cpfLedger_sumsAllFourComponents() {
        stubEmploymentCategoryBasicPay(new BigDecimal("30000.00"));
        stubDaRate(new BigDecimal("10.00"));
        CpfBalanceLedger ledger = new CpfBalanceLedger(employee, new BigDecimal("10000.00"), new BigDecimal("8000.00"), new BigDecimal("2000.00"));
        when(cpfBalanceLedgerRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(Optional.of(ledger));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1), null, new BigDecimal("500.00"));

        assertThat(c.cpfEmployeeBalance()).isEqualByComparingTo("10000.00");
        assertThat(c.cpfEmployerBalance()).isEqualByComparingTo("8000.00");
        assertThat(c.cpfVpfBalance()).isEqualByComparingTo("2000.00");
        assertThat(c.cpfAccruedInterest()).isEqualByComparingTo("500.00");
        assertThat(c.totalCpfPayable()).isEqualByComparingTo("20500.00");
    }

    @Test
    void calculate_cpfLedger_missingRow_defaultsToZero() {
        stubEmploymentCategoryBasicPay(new BigDecimal("30000.00"));
        stubDaRate(new BigDecimal("10.00"));

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.totalCpfPayable()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------
    // Beneficiary 100% split validation
    // -------------------------------------------------------------------
    @Test
    void replaceBeneficiaries_sharesNotSummingTo100_throws() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

        List<TerminalSettlementBeneficiaryRequest> requests = List.of(
                new TerminalSettlementBeneficiaryRequest(BeneficiaryType.NOMINEE, "A", "Spouse",
                        new BigDecimal("60.00"), new BigDecimal("60000.00"), "111", "IFSC0001", "Bank", "PAN1"),
                new TerminalSettlementBeneficiaryRequest(BeneficiaryType.NOMINEE, "B", "Son",
                        new BigDecimal("30.00"), new BigDecimal("30000.00"), "222", "IFSC0002", "Bank", "PAN2"));

        assertThatThrownBy(() -> service.replaceBeneficiaries(1L, requests))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("100.00");
    }

    @Test
    void replaceBeneficiaries_amountsNotMatchingNetPayable_throws() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

        List<TerminalSettlementBeneficiaryRequest> requests = List.of(
                new TerminalSettlementBeneficiaryRequest(BeneficiaryType.NOMINEE, "A", "Spouse",
                        new BigDecimal("100.00"), new BigDecimal("50000.00"), "111", "IFSC0001", "Bank", "PAN1"));

        assertThatThrownBy(() -> service.replaceBeneficiaries(1L, requests))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("net terminal payable");
    }

    @Test
    void replaceBeneficiaries_validSplit_persists() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TerminalSettlementBeneficiaryRequest> requests = List.of(
                new TerminalSettlementBeneficiaryRequest(BeneficiaryType.NOMINEE, "A", "Spouse",
                        new BigDecimal("60.00"), new BigDecimal("60000.00"), "111", "IFSC0001", "Bank", "PAN1"),
                new TerminalSettlementBeneficiaryRequest(BeneficiaryType.NOMINEE, "B", "Son",
                        new BigDecimal("40.00"), new BigDecimal("40000.00"), "222", "IFSC0002", "Bank", "PAN2"));

        List<TerminalSettlementBeneficiary> saved = service.replaceBeneficiaries(1L, requests);

        assertThat(saved).hasSize(2);
    }

    @Test
    void approve_withoutBeneficiaries_throws() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        when(beneficiaryRepository.findBySettlementId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.approve(1L)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approve_withValidAllocation_setsApproved() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        TerminalSettlementBeneficiary self = new TerminalSettlementBeneficiary(settlement, BeneficiaryType.SELF, "Self", "SELF",
                new BigDecimal("100.00"), new BigDecimal("100000.00"), "111", "IFSC0001", "Bank", "PAN1");
        when(beneficiaryRepository.findBySettlementId(1L)).thenReturn(List.of(self));

        TerminalSettlement approved = service.approve(1L);

        assertThat(approved.getStatus()).isEqualTo(TerminalSettlementStatus.APPROVED);
    }

    @Test
    void approve_missingBankDetails_throws() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        TerminalSettlementBeneficiary noBank = new TerminalSettlementBeneficiary(settlement, BeneficiaryType.NOMINEE, "Nominee", "Spouse",
                new BigDecimal("100.00"), new BigDecimal("100000.00"), null, null, null, null);
        when(beneficiaryRepository.findBySettlementId(1L)).thenReturn(List.of(noBank));

        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("bank account");
    }

    @Test
    void approve_alreadyApproved_throws() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(settlement, "status", TerminalSettlementStatus.APPROVED);
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already");
    }

    // -------------------------------------------------------------------
    // Leave ledger debit on settlement approval
    // -------------------------------------------------------------------
    @Test
    void approve_debitsElAndHplBalances_andWritesLedgerEntries() {
        TerminalSettlement settlement = draftSettlementWithEncashment(new BigDecimal("100000.00"),
                new BigDecimal("250.00"), new BigDecimal("50.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        TerminalSettlementBeneficiary self = new TerminalSettlementBeneficiary(settlement, BeneficiaryType.SELF, "Self", "SELF",
                new BigDecimal("100.00"), new BigDecimal("100000.00"), "111", "IFSC0001", "Bank", "PAN1");
        when(beneficiaryRepository.findBySettlementId(1L)).thenReturn(List.of(self));

        LeaveEntitlementBalance elBalance = new LeaveEntitlementBalance(employee, elType, 2026);
        elBalance.setEncashableCurrent(new BigDecimal("250.00"));
        elBalance.setEncashableEncashed(BigDecimal.ZERO);
        elBalance.setEncashedDays(BigDecimal.ZERO);
        elBalance.setCurrentBalance(new BigDecimal("250.00"));
        when(leaveEntitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, EL_TYPE_ID, 2026))
                .thenReturn(Optional.of(elBalance));

        LeaveBalance hplBalance = new LeaveBalance(employee, hplType, 2026, new BigDecimal("50.0"));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_TYPE_ID, 2026))
                .thenReturn(Optional.of(hplBalance));

        service.approve(1L);

        assertThat(elBalance.getEncashableCurrent()).isEqualByComparingTo("0.00");
        assertThat(elBalance.getEncashableEncashed()).isEqualByComparingTo("250.00");
        assertThat(elBalance.getEncashedDays()).isEqualByComparingTo("250.00");
        assertThat(elBalance.getCurrentBalance()).isEqualByComparingTo("0.00");
        assertThat(hplBalance.getUsedDays()).isEqualByComparingTo("50.0");

        org.mockito.Mockito.verify(leaveLedgerEntryRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.argThat(entry -> entry.getSource() == LeaveLedgerSource.TERMINAL_ENCASHMENT));
    }

    @Test
    void approve_zeroEncashedDays_skipsLeaveDebitEntirely() {
        TerminalSettlement settlement = draftSettlement(new BigDecimal("100000.00"));
        when(terminalSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
        TerminalSettlementBeneficiary self = new TerminalSettlementBeneficiary(settlement, BeneficiaryType.SELF, "Self", "SELF",
                new BigDecimal("100.00"), new BigDecimal("100000.00"), "111", "IFSC0001", "Bank", "PAN1");
        when(beneficiaryRepository.findBySettlementId(1L)).thenReturn(List.of(self));

        service.approve(1L);

        org.mockito.Mockito.verifyNoInteractions(leaveLedgerEntryRepository);
    }

    // -------------------------------------------------------------------
    // Deceased case beneficiary auto-population from employee_nominees
    // -------------------------------------------------------------------
    @Test
    void generate_deceasedCase_seedsNomineeBeneficiaries() {
        stubEmploymentCategoryBasicPay(new BigDecimal("40000.00"));
        stubDaRate(new BigDecimal("10.00"));

        EmployeeNominee spouse = new EmployeeNominee(employee, "Jane Doe", "Spouse", new BigDecimal("60.00"), "GRATUITY");
        EmployeeNominee son = new EmployeeNominee(employee, "John Doe Jr.", "Son", new BigDecimal("40.00"), "GRATUITY");
        when(employeeNomineeRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(spouse, son));
        when(terminalSettlementRepository.save(any())).thenAnswer(inv -> {
            TerminalSettlement s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 9L);
            return s;
        });
        when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generate(EMPLOYEE_ID, SeparationType.DECEASED, LocalDate.of(2026, 1, 1), null, null);

        org.mockito.ArgumentCaptor<TerminalSettlementBeneficiary> captor = org.mockito.ArgumentCaptor.forClass(TerminalSettlementBeneficiary.class);
        org.mockito.Mockito.verify(beneficiaryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<TerminalSettlementBeneficiary> saved = captor.getAllValues();

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getBeneficiaryType()).isEqualTo(BeneficiaryType.NOMINEE);
        assertThat(saved.get(0).getBeneficiaryName()).isEqualTo("Jane Doe");
        assertThat(saved.get(0).getRelationship()).isEqualTo("Spouse");
        assertThat(saved.get(0).getSharePercentage()).isEqualByComparingTo("60.00");
        assertThat(saved.get(0).getBankAccountNo()).isNull();
        assertThat(saved.get(0).getBankIfsc()).isNull();
        // SELF/bank-account resolution must never be consulted for a DECEASED settlement.
        org.mockito.Mockito.verifyNoInteractions(employeeBankAccountRepository);
    }

    @Test
    void generate_deceasedCase_noRegisteredNominees_seedsEmptyList() {
        stubEmploymentCategoryBasicPay(new BigDecimal("40000.00"));
        stubDaRate(new BigDecimal("10.00"));
        when(employeeNomineeRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of());
        when(terminalSettlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generate(EMPLOYEE_ID, SeparationType.DECEASED, LocalDate.of(2026, 1, 1), null, null);

        org.mockito.Mockito.verifyNoInteractions(beneficiaryRepository);
    }

    // -------------------------------------------------------------------
    // Null-safety on legacy/missing scale links and leave balances
    // -------------------------------------------------------------------
    @Test
    void calculate_noPayFixationAndNoLegacyPayScale_fallsBackToIdaWithoutNpe() {
        stubEmploymentCategoryBasicPay(new BigDecimal("30000.00"));
        stubDaRate(new BigDecimal("10.00"));
        // employee fixture (setUp) has no PayScale set - resolveEmoluments must still resolve ScaleType.IDA, not NPE.

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.daRatePercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_bothLeaveBalanceRowsMissing_zeroEncashmentNoNpe() {
        stubEmploymentCategoryBasicPay(new BigDecimal("30000.00"));
        stubDaRate(new BigDecimal("10.00"));
        // Default @BeforeEach stubs already return Optional.empty() for both EL and HPL balance lookups.

        TerminalSettlementCalculation c = service.preview(EMPLOYEE_ID, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1), null, null);

        assertThat(c.elBalanceAtRetirement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.hplBalanceAtRetirement()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.elDaysEncashed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.hplDaysEncashed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.totalLeaveEncashment()).isEqualByComparingTo("0.00");
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------
    private Employee newEmployeeWithJoining(LocalDate dateOfJoining) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        Employee e = new Employee("2801", "Test", "Employee", "test@example.com", dateOfJoining, department, designation);
        ReflectionTestUtils.setField(e, "id", EMPLOYEE_ID);
        return e;
    }

    /** Zero elDaysEncashed/hplDaysEncashed - these callers test beneficiary validation, not leave debiting, so approve() should skip debitEncashedLeave() entirely (see draftSettlementWithEncashment for the one test that does exercise it). */
    private TerminalSettlement draftSettlement(BigDecimal netPayable) {
        return draftSettlementWithEncashment(netPayable, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private TerminalSettlement draftSettlementWithEncashment(BigDecimal netPayable, BigDecimal elDaysEncashed, BigDecimal hplDaysEncashed) {
        TerminalSettlement settlement = new TerminalSettlement(employee, null, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1),
                new BigDecimal("50000.00"), new BigDecimal("10.00"), new BigDecimal("5000.00"),
                10, 0, new BigDecimal("100.00"), new BigDecimal("50.00"), elDaysEncashed, hplDaysEncashed,
                new BigDecimal("18333.33"), new BigDecimal("4583.33"), new BigDecimal("22916.66"),
                new BigDecimal("50000.00"), false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                netPayable, BigDecimal.ZERO, netPayable);
        ReflectionTestUtils.setField(settlement, "id", 1L);
        return settlement;
    }
}
