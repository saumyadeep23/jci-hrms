package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollMonthlyRecordResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;
import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.PayrollTaxOverrideRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.TransportAllowanceRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollBatchComputationServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int HEAD_HRA = 9;
    private static final int HEAD_TRANSPORT = 10;
    private static final int HEAD_ENCASHMENT = 20;
    private static final int HEAD_NPS = 62;
    private static final int HEAD_TDS = 40;
    private static final int HEAD_CEA = 22;
    private static final int STAT_HEAD_EMPLOYER_EPF = 1;
    private static final int STAT_HEAD_EMPLOYER_JCPF = 3;
    private static final int STAT_HEAD_EMPLOYER_PENSION = 4;
    private static final int STAT_HEAD_EMPLOYER_NPS = 15;

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private DaRateHistoryRepository daRateHistoryRepository;
    @Mock private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock private PayrollHraRateRepository payrollHraRateRepository;
    @Mock private TransportAllowanceRateRepository transportAllowanceRateRepository;
    @Mock private PtaxSlabRepository ptaxSlabRepository;
    @Mock private StateMasterRepository stateMasterRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    @Mock private EmployeeNpsDeclarationRepository npsDeclarationRepository;
    @Mock private EmployeeVehicleAllotmentRepository vehicleAllotmentRepository;
    @Mock private EmployeeQuarterAllotmentService quarterAllotmentService;
    @Mock private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock private PayrollMovementInputRepository payrollMovementInputRepository;
    @Mock private PayrollTdsEngine payrollTdsEngine;
    @Mock private PayrollTaxOverrideRepository payrollTaxOverrideRepository;
    @Mock private EmployeeCeaClaimRepository ceaClaimRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository suspensionRecordRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository suspensionNecRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository deputationRecordRepository;
    @Mock private PayrollQueryService payrollQueryService;
    @Mock private CpfLoanPayrollRecoveryResolverService cpfLoanPayrollRecoveryResolverService;
    @Mock private JciEccsPayrollRecoveryResolverService jciEccsPayrollRecoveryResolverService;

    private PayrollBatchComputationService service;

    private PayrollBatch batch;
    private Employee employee;
    private RegularPayFixation fixation;
    private GradeScaleMaster gradeScale;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void setUp() {
        service = new PayrollBatchComputationService(payrollBatchRepository, payrollMonthlyRecordRepository,
                payrollMonthlyHeadItemRepository, payrollMonthlyStatutoryItemRepository, employeeRepository, regularPayFixationRepository, daRateHistoryRepository,
                dailyAttendanceRepository, payrollHraRateRepository, transportAllowanceRateRepository, ptaxSlabRepository,
                stateMasterRepository, payrollStatutoryParameterRepository, npsDeclarationRepository, vehicleAllotmentRepository,
                quarterAllotmentService, encashmentRepository, payrollMovementInputRepository, payrollTdsEngine, payrollTaxOverrideRepository, ceaClaimRepository,
                suspensionRecordRepository, suspensionNecRepository, deputationRecordRepository, payrollQueryService,
                cpfLoanPayrollRecoveryResolverService, jciEccsPayrollRecoveryResolverService);

        batch = new PayrollBatch("BATCH-2026-08", 8, 2026, "2026-2027");
        ReflectionTestUtils.setField(batch, "id", 100L);
        batch.setStatus(PayrollBatchStatus.DRAFT);
        when(cpfLoanPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(CpfLoanPayrollRecoveryResolverService.RecoveryAmounts.ZERO);
        when(jciEccsPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(JciEccsPayrollRecoveryResolverService.RecoveryAmounts.ZERO);

        periodStart = LocalDate.of(2026, 8, 1);
        periodEnd = LocalDate.of(2026, 8, 31);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setStatus(EmployeeStatus.ACTIVE);

        gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false, new BigDecimal("50000.00"), new BigDecimal("80000.00"));
        ReflectionTestUtils.setField(gradeScale, "id", 5L);
        gradeScale.setScaleType(ScaleType.IDA);

        fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2020, 1, 15));

        // Default happy-path stubs - individual tests override what they need to exercise.
        when(payrollBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(payrollMonthlyRecordRepository.deleteByBatch_Id(100L)).thenReturn(0L);
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee));
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(fixation));
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, periodEnd)).thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L, periodStart, periodEnd)).thenReturn(List.of());
        when(quarterAllotmentService.isHraSuppressed(1L, periodStart, periodEnd)).thenReturn(false);
        when(quarterAllotmentService.findActiveOccupancy(1L, periodStart, periodEnd)).thenReturn(Optional.empty());
        when(payrollHraRateRepository.findActiveOn(periodEnd)).thenReturn(List.of(hraRate(CityClass.Z, "10.00")));
        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(5L, "Z"))
                .thenReturn(List.of());
        when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(eq(1L), any())).thenReturn(List.of());
        when(npsDeclarationRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("CPF_EMP_RATE")).thenReturn(Optional.empty());
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of());
        when(encashmentRepository.findByPayrollBatch_IdAndPayrollProcessedFalse(100L)).thenReturn(List.of());
        when(payrollTdsEngine.computeMonthlyTds(any(), anyString(), anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PayrollTdsEngine.TdsResult(BigDecimal.ZERO, BigDecimal.ZERO, false, null, 8, BigDecimal.ZERO, BigDecimal.ZERO));
        when(payrollMonthlyRecordRepository.saveAndFlush(any(PayrollMonthlyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PayrollHraRate hraRate(CityClass cityClass, String percentage) {
        return new PayrollHraRate(cityClass.name(), new BigDecimal(percentage), BigDecimal.ZERO, LocalDate.of(2024, 1, 1), null, null);
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(payrollMonthlyHeadItemRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> amountForHead(int headCount) {
        return capturedHeadItems().stream()
                .filter(item -> item.getHeadCount() == headCount)
                .map(PayrollMonthlyHeadItem::getAmount)
                .findFirst();
    }

    private List<in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem> capturedStatutoryItems() {
        ArgumentCaptor<in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem> captor =
                ArgumentCaptor.forClass(in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem.class);
        verify(payrollMonthlyStatutoryItemRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> amountForStatHead(int statHeadCount) {
        return capturedStatutoryItems().stream()
                .filter(item -> item.getStatHeadCount() == statHeadCount)
                .map(in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem::getAmount)
                .findFirst();
    }

    // ---- HRA suppression ----

    @Test
    void processBatch_activeAccommodation_suppressesHraToZero() {
        when(quarterAllotmentService.isHraSuppressed(1L, periodStart, periodEnd)).thenReturn(true);

        service.processBatch(100L);

        assertThat(amountForHead(HEAD_HRA)).isEmpty(); // zero amounts are never inserted
    }

    @Test
    void processBatch_noAccommodation_computesHraFromActiveRate() {
        service.processBatch(100L);

        // Earned Basic 50000 * 10% (Z class) = 5000
        assertThat(amountForHead(HEAD_HRA)).contains(new BigDecimal("5000"));
    }

    // ---- Transport Allowance suppression ----

    @Test
    void processBatch_officeCarProvided_suppressesTransportAllowanceToZero() {
        employee.setOfficeCarProvided(true);
        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(5L, "Z"))
                .thenReturn(List.of(new in.gov.jci.hrms.entity.TransportAllowanceRate(gradeScale, "Z", new BigDecimal("1800.00"), LocalDate.of(2020, 4, 1))));

        service.processBatch(100L);

        assertThat(amountForHead(HEAD_TRANSPORT)).isEmpty();
    }

    @Test
    void processBatch_noOfficeCar_computesTransportAllowanceFromRate() {
        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(5L, "Z"))
                .thenReturn(List.of(new in.gov.jci.hrms.entity.TransportAllowanceRate(gradeScale, "Z", new BigDecimal("1800.00"), LocalDate.of(2020, 4, 1))));

        service.processBatch(100L);

        // 1800 base + round(1800 * 17%) = 1800 + 306 = 2106
        assertThat(amountForHead(HEAD_TRANSPORT)).contains(new BigDecimal("2106"));
    }

    // ---- NPS deduction ----

    @Test
    void processBatch_activeNpsDeclarationAndPranOnFile_deductsDeclaredPercentageOfBasicPlusDa() {
        employee.setPranNumber("123456789012");
        EmployeeNpsDeclaration declaration = new EmployeeNpsDeclaration(employee, "2026-2027", new BigDecimal("5.00"),
                LocalDate.of(2026, 4, 1), null);
        when(npsDeclarationRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.of(declaration));

        service.processBatch(100L);

        // Earned Basic 50000 + DA (17% = 8500) = 58500; 5% of that = 2925
        assertThat(amountForHead(HEAD_NPS)).contains(new BigDecimal("2925"));
    }

    @Test
    void processBatch_activeNpsDeclarationButNoPranOnFile_noNpsDeduction() {
        // NPS is mandatory once declared, but no deduction (nor the employer Sec 80CCD(2) TDS
        // exemption estimate) may fire until the employee's PRAN is actually on file - see
        // PayrollBatchComputationService.resolveNps()'s own javadoc. employee.pranNumber is null by
        // default in setUp() - not set here, deliberately.
        EmployeeNpsDeclaration declaration = new EmployeeNpsDeclaration(employee, "2026-2027", new BigDecimal("5.00"),
                LocalDate.of(2026, 4, 1), null);
        when(npsDeclarationRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.of(declaration));

        service.processBatch(100L);

        assertThat(amountForHead(HEAD_NPS)).isEmpty();
    }

    @Test
    void processBatch_noNpsDeclaration_noNpsDeduction() {
        service.processBatch(100L);

        assertThat(amountForHead(HEAD_NPS)).isEmpty();
    }

    // ---- Employer-side statutory contributions (payroll_monthly_statutory_items, never the payslip) ----

    @Test
    void processBatch_notEpsOrNpsEligible_postsFullCpfToJcpfWithNoPensionOrNps() {
        // employee.isEpsEligible()/isNpsEligible() are both false by default in setUp(). CPF (stat
        // head 1) is unaffected by either flag - it mirrors computeCpf()'s own unconditional
        // application (58500 * 12% = 7020). With no EPS pension carve-out, JCPF (stat head 3) absorbs
        // the entire CPF amount (7020 - 0) rather than being dropped - see resolveEmployerContributions().
        service.processBatch(100L);

        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).isEmpty();
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_NPS)).isEmpty();
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("7020"));
    }

    @Test
    void processBatch_epsEligible_splitsCpfIntoPensionCarveOutAndJcpfRemainder() {
        employee.setEpsEligible(true);

        service.processBatch(100L);

        // Basic+DA = 58500; CPF (stat head 1, unconditional) = 7020; pension carve-out
        // round(8.33% of min(58500,15000)) = round(1249.5) = 1250 (HALF_UP); JCPF remainder = 7020 - 1250 = 5770
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).contains(new BigDecimal("1250"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("5770"));
    }

    @Test
    void processBatch_epsHigherPensionEligibleAboveWageCeiling_addsExtraRateOnTopOfPensionCarveOut() {
        employee.setEpsEligible(true);
        employee.setEpsHigherPensionEligible(true);

        service.processBatch(100L);

        // Base pension carve-out round(1249.5)=1250 (HALF_UP) + FLOOR(1.16% of the excess above the
        // 15000 ceiling, 58500-15000=43500): floor(43500*1.16%)=floor(504.60)=504 -> 1250+504=1754.
        // The higher-pension extra floors rather than rounding HALF_UP - see resolveEmployerContributions()'s
        // own javadoc. CPF (stat head 1) is unaffected by the extra rate; JCPF absorbs the reduced remainder.
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).contains(new BigDecimal("1754"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("5266"));
    }

    @Test
    void processBatch_epsHigherPensionEligibleFlagButNotEpsEligible_noExtraRateApplied() {
        // isEpsHigherPensionEligible() alone, without isEpsEligible(), computes nothing - matches
        // EmployeeService.create()'s own invariant that higher-pension can only be true alongside EPS
        // eligibility, but resolveEmployerContributions() re-checks isEpsEligible() independently rather
        // than assuming that invariant always held for pre-existing data. CPF/JCPF stay unconditional.
        employee.setEpsHigherPensionEligible(true);

        service.processBatch(100L);

        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).isEmpty();
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("7020"));
    }

    @Test
    void processBatch_npsEligibleWithPranOnFile_postsEmployerNpsContribution() {
        employee.setNpsEligible(true);
        employee.setPranNumber("123456789012");

        service.processBatch(100L);

        // Basic+DA 58500 * 10% (default NPS_EMPLOYER_RATE) = 5850
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_NPS)).contains(new BigDecimal("5850"));
    }

    @Test
    void processBatch_npsEligibleButNoPranOnFile_noEmployerNpsContribution() {
        employee.setNpsEligible(true);

        service.processBatch(100L);

        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_NPS)).isEmpty();
    }

    // ---- Head 22 CEA reimbursement: payroll pickup ----

    private in.gov.jci.hrms.entity.EmployeeCeaClaim billPassedCeaClaim(BigDecimal passedAmount) {
        in.gov.jci.hrms.entity.EmployeeDependent dependent = new in.gov.jci.hrms.entity.EmployeeDependent(
                employee, "Junior Rao", in.gov.jci.hrms.entity.FamilyRelationshipType.SON, true, false);
        ReflectionTestUtils.setField(dependent, "id", 900L);
        in.gov.jci.hrms.entity.EmployeeCeaClaim claim = new in.gov.jci.hrms.entity.EmployeeCeaClaim(
                "CEA/2026/001", employee, dependent, "2026-2027", in.gov.jci.hrms.entity.CeaClaimType.CEA,
                "ABC School", "V", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                new BigDecimal("30000.00"), new BigDecimal("28125.00"));
        claim.setClaimStatus(in.gov.jci.hrms.entity.CeaClaimStatus.BILL_PASSED);
        claim.setPassedAmount(passedAmount);
        ReflectionTestUtils.setField(claim, "id", 700L);
        return claim;
    }

    @Test
    void processBatch_billPassedCeaClaimPending_postsHead22AndMarksClaimDisbursed() {
        in.gov.jci.hrms.entity.EmployeeCeaClaim claim = billPassedCeaClaim(new BigDecimal("2812.50"));
        when(ceaClaimRepository.findPendingPayrollDisbursement(1L)).thenReturn(List.of(claim));

        service.processBatch(100L);

        // round(2812.50) = 2813 (HALF_UP) - matches the whole-rupee head-amount policy.
        assertThat(amountForHead(HEAD_CEA)).contains(new BigDecimal("2813"));
        assertThat(claim.isPayrollProcessed()).isTrue();
        assertThat(claim.getPayrollBatch()).isEqualTo(batch);
        assertThat(claim.getClaimStatus()).isEqualTo(in.gov.jci.hrms.entity.CeaClaimStatus.DISBURSED);
    }

    @Test
    void processBatch_noPendingCeaClaims_postsNoHead22() {
        service.processBatch(100L);

        assertThat(amountForHead(HEAD_CEA)).isEmpty();
    }

    // ---- Head 20 Leave Encashment: 25th cutoff ----

    private LeaveEncashmentApplication encashmentApprovedAt(Instant financeApprovedAt) {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("10.00"), BigDecimal.ZERO);
        application.setFinanceApprovalStatus(ApprovalStatus.APPROVED);
        application.setPayrollEligible(true);
        ReflectionTestUtils.setField(application, "financeApprovedAt", financeApprovedAt);
        ReflectionTestUtils.setField(application, "id", 500L);
        return application;
    }

    @Test
    void processBatch_encashmentApprovedByThe25th_isIncludedInHead20() {
        Instant approvedOn24th = LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication application = encashmentApprovedAt(approvedOn24th);
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of(application));

        service.processBatch(100L);

        // gross_amount not populated -> fallback formula: (basic+da)/30 * days = (58500/30)*10 = 19500
        assertThat(amountForHead(HEAD_ENCASHMENT)).contains(new BigDecimal("19500"));
        assertThat(application.getPayrollBatch()).isEqualTo(batch);
    }

    @Test
    void processBatch_encashmentApprovedOn26thOrLater_isExcludedFromHead20() {
        Instant approvedOn26th = LocalDateTime.of(2026, 8, 26, 0, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication application = encashmentApprovedAt(approvedOn26th);
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of(application));

        service.processBatch(100L);

        assertThat(amountForHead(HEAD_ENCASHMENT)).isEmpty();
        assertThat(application.getPayrollBatch()).isNull();
    }

    @Test
    void processBatch_superannuationOrSeparationEncashment_isExcludedFromRegularMonthlyBatch() {
        // SUPERANNUATION/SEPARATION encashments are terminal settlements, disbursed once at actual
        // separation via TerminalSettlementService - not bundled into an ordinary payslip while the
        // employee is still ACTIVE, even if Finance-approved well within the 25th-of-month cutoff and
        // otherwise payroll_eligible. Only IN_SERVICE_EL flows through this regular monthly batch.
        Instant approvedOn6th = LocalDateTime.of(2026, 8, 6, 10, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication inServiceEl = encashmentApprovedAt(approvedOn6th);
        LeaveEncashmentApplication superannuation = new LeaveEncashmentApplication(employee, EncashmentType.SUPERANNUATION,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        superannuation.setFinanceApprovalStatus(ApprovalStatus.APPROVED);
        superannuation.setPayrollEligible(true);
        ReflectionTestUtils.setField(superannuation, "financeApprovedAt", approvedOn6th);
        ReflectionTestUtils.setField(superannuation, "id", 501L);
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L))
                .thenReturn(List.of(inServiceEl, superannuation));

        service.processBatch(100L);

        // IN_SERVICE_EL still included (fallback formula: (58500/30)*10 = 19500) - the SUPERANNUATION
        // application contributes nothing to Head 20 and is left completely untagged, for
        // TerminalSettlementService (or whatever eventually runs it) to pick up on its own terms.
        assertThat(amountForHead(HEAD_ENCASHMENT)).contains(new BigDecimal("19500"));
        assertThat(inServiceEl.getPayrollBatch()).isEqualTo(batch);
        assertThat(superannuation.getPayrollBatch()).isNull();
    }

    @Test
    void processBatch_usesPopulatedGrossAmountOverFallbackFormulaWhenPositive() {
        Instant approvedOn24th = LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication application = encashmentApprovedAt(approvedOn24th);
        application.setGrossAmount(new BigDecimal("12345.00"));
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of(application));

        service.processBatch(100L);

        assertThat(amountForHead(HEAD_ENCASHMENT)).contains(new BigDecimal("12345"));
    }

    // ---- Head 20 payroll_batch_id lifecycle: tag on draft, release on re-run, lock on finalize ----

    @Test
    void processBatch_tagsEncashmentWithBatchId() {
        Instant approvedOn24th = LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication application = encashmentApprovedAt(approvedOn24th);
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of(application));

        service.processBatch(100L);

        assertThat(application.getPayrollBatch()).isEqualTo(batch);
        assertThat(application.isPayrollProcessed()).isFalse();
    }

    @Test
    void processBatch_rerun_releasesPreviouslyTaggedUnprocessedApplicationsFirst() {
        LeaveEncashmentApplication staleTag = encashmentApprovedAt(LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant());
        staleTag.setPayrollBatch(batch);
        when(encashmentRepository.findByPayrollBatch_IdAndPayrollProcessedFalse(100L)).thenReturn(List.of(staleTag));
        // No longer eligible on re-run (e.g. Finance revoked approval) - findEligibleForPayrollBatch returns empty.
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of());

        service.processBatch(100L);

        assertThat(staleTag.getPayrollBatch()).isNull();
    }

    @Test
    void finalizeBatch_locksTaggedEncashmentsAsPayrollProcessed() {
        LeaveEncashmentApplication tagged = encashmentApprovedAt(LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant());
        tagged.setPayrollBatch(batch);
        when(payrollMonthlyRecordRepository.findByBatch_Id(100L)).thenReturn(List.of(new PayrollMonthlyRecord(
                batch, employee, "EMP-001", 8, 2026, null, null, "Z", "IDA", 31)));
        when(encashmentRepository.findByPayrollBatch_Id(100L)).thenReturn(List.of(tagged));
        batch.setStatus(PayrollBatchStatus.CALCULATED);

        service.finalizeBatch(100L, null);

        assertThat(tagged.isPayrollProcessed()).isTrue();
        assertThat(batch.getStatus()).isEqualTo(PayrollBatchStatus.HR_FINALIZED);
    }

    @Test
    void finalizeBatch_withNoComputedRecords_throws() {
        batch.setStatus(PayrollBatchStatus.CALCULATED);
        when(payrollMonthlyRecordRepository.findByBatch_Id(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.finalizeBatch(100L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void finalizeBatch_whenAlreadyFinalized_throws() {
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        assertThatThrownBy(() -> service.finalizeBatch(100L, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- TDS pro-rata: Head 20 feeds into gross passed to the TDS engine without inflating regularMonthlyGross ----

    @Test
    void processBatch_passesFullGrossButExcludesEncashmentFromRegularMonthlyGrossToTdsEngine() {
        Instant approvedOn24th = LocalDateTime.of(2026, 8, 24, 10, 0).atZone(IST).toInstant();
        LeaveEncashmentApplication application = encashmentApprovedAt(approvedOn24th);
        application.setGrossAmount(new BigDecimal("20000.00"));
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 100L)).thenReturn(List.of(application));

        service.processBatch(100L);

        // Regular gross (no encashment): basic 50000 + DA 8500 + HRA 5000 + TA 0 = 63500.
        // Full gross (with encashment): 63500 + 20000 = 83500.
        verify(payrollTdsEngine).computeMonthlyTds(eq(employee), eq("2026-2027"), eq(8), eq(2026),
                eq(new BigDecimal("83500")), eq(new BigDecimal("63500")), any(), anyBoolean());
    }

    // ---- batch summary roll-up equals sum of employee records ----

    @Test
    void processBatch_batchTotals_equalSumOfEmployeeRecords() {
        Employee secondEmployee = new Employee("EMP-002", "Bala", "Iyer", "bala.iyer@example.com",
                LocalDate.of(2021, 3, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(secondEmployee, "id", 2L);
        secondEmployee.setStatus(EmployeeStatus.ACTIVE);
        RegularPayFixation secondFixation = new RegularPayFixation(secondEmployee, gradeScale, new BigDecimal("40000.00"), LocalDate.of(2021, 3, 1));

        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee, secondEmployee));
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(2L)).thenReturn(List.of(secondFixation));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(2L, periodStart, periodEnd)).thenReturn(List.of());
        when(quarterAllotmentService.isHraSuppressed(2L, periodStart, periodEnd)).thenReturn(false);
        when(quarterAllotmentService.findActiveOccupancy(2L, periodStart, periodEnd)).thenReturn(Optional.empty());

        PayrollBatch result = service.processBatch(100L);

        ArgumentCaptor<PayrollMonthlyRecord> recordCaptor = ArgumentCaptor.forClass(PayrollMonthlyRecord.class);
        verify(payrollMonthlyRecordRepository, times(2)).saveAndFlush(recordCaptor.capture());
        BigDecimal sumGross = recordCaptor.getAllValues().stream().map(PayrollMonthlyRecord::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDeductions = recordCaptor.getAllValues().stream().map(PayrollMonthlyRecord::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumNet = recordCaptor.getAllValues().stream().map(PayrollMonthlyRecord::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(result.getTotalEmployees()).isEqualTo(2);
        assertThat(result.getTotalGross()).isEqualByComparingTo(sumGross);
        assertThat(result.getTotalDeductions()).isEqualByComparingTo(sumDeductions);
        assertThat(result.getTotalNet()).isEqualByComparingTo(sumNet);
    }

    @Test
    void processBatch_employeeWithNoCurrentPayFixation_isSkippedNotFailed() {
        Employee noFixationEmployee = new Employee("EMP-003", "Chitra", "Nair", "chitra.nair@example.com",
                LocalDate.of(2022, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(noFixationEmployee, "id", 3L);
        noFixationEmployee.setStatus(EmployeeStatus.ACTIVE);

        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee, noFixationEmployee));
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(3L)).thenReturn(List.of());

        PayrollBatch result = service.processBatch(100L);

        assertThat(result.getTotalEmployees()).isEqualTo(1);
    }

    @Test
    void processBatch_whenBatchNotDraft_throws() {
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);

        assertThatThrownBy(() -> service.processBatch(100L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void processBatch_setsStatusToCalculated() {
        PayrollBatch result = service.processBatch(100L);

        assertThat(result.getStatus()).isEqualTo(PayrollBatchStatus.CALCULATED);
    }

    @Test
    void processBatch_whenAlreadyCalculated_allowsRecompute() {
        batch.setStatus(PayrollBatchStatus.CALCULATED);

        PayrollBatch result = service.processBatch(100L);

        assertThat(result.getStatus()).isEqualTo(PayrollBatchStatus.CALCULATED);
    }

    // ---- listRecords ----

    @Test
    void listRecords_flagsTdsOverriddenWhenOverridePresent() {
        PayrollMonthlyRecord record = new PayrollMonthlyRecord(batch, employee, "EMP-001", 8, 2026, null, null, "Z", "IDA", 31);
        ReflectionTestUtils.setField(record, "tranId", 900L);
        when(payrollMonthlyRecordRepository.findByBatch_Id(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record)));
        when(payrollMonthlyHeadItemRepository.findByRecord_TranId(900L)).thenReturn(List.of());
        when(payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(1L, 2026, 8))
                .thenReturn(Optional.of(new in.gov.jci.hrms.entity.PayrollTaxOverride(employee, "2026-2027", 8, 2026,
                        BigDecimal.ZERO, new BigDecimal("500.00"), "Bill Section correction for arrears", "officer",
                        in.gov.jci.hrms.entity.TaxOverrideScope.MONTH_ONLY)));

        Page<PayrollMonthlyRecordResponse> page = service.listRecords(100L, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).tdsOverridden()).isTrue();
    }
}
