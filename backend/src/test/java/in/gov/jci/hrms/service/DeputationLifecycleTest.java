package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeputationInitiateRequest;
import in.gov.jci.hrms.dto.DeputationRepatriationRequest;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.DeputationStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayOption;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeputationLifecycleTest {

    private static final int HEAD_DEP_ALLOW = 67;

    // ---- DeputationLifecycleService unit tests ----

    @Mock private EmployeeDeputationRecordRepository deputationRepository;
    @Mock private EmployeeRepository employeeRepository;

    private DeputationLifecycleService lifecycleService;
    private Employee employee;
    private GradeScaleMaster gradeScale;

    @BeforeEach
    void setUp() {
        lifecycleService = new DeputationLifecycleService(deputationRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setStatus(EmployeeStatus.ACTIVE);

        gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false, new BigDecimal("50000.00"), new BigDecimal("80000.00"));
        gradeScale.setScaleType(ScaleType.IDA);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(deputationRepository.save(any(EmployeeDeputationRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initiateDeputation_sameStation_appliesFivePercentRateAndFortyFiveHundredCap() {
        DeputationInitiateRequest request = new DeputationInitiateRequest(1L, DeputationDirection.DEPUTATION_OUT, "NTPC", "PSU",
                "Delhi", true, LocalDate.now(), LocalDate.now().plusYears(2), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE,
                true, null, null);

        EmployeeDeputationRecord record = lifecycleService.initiateDeputation(request);

        assertThat(record.getDeputationAllowanceRate()).isEqualByComparingTo("5.00");
        assertThat(record.getDeputationAllowanceCap()).isEqualByComparingTo("4500.00");
    }

    @Test
    void initiateDeputation_differentStation_appliesTenPercentRateAndNineThousandCap() {
        DeputationInitiateRequest request = new DeputationInitiateRequest(1L, DeputationDirection.DEPUTATION_OUT, "SAIL", "PSU",
                "Kolkata", false, LocalDate.now(), LocalDate.now().plusYears(2), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE,
                true, null, null);

        EmployeeDeputationRecord record = lifecycleService.initiateDeputation(request);

        assertThat(record.getDeputationAllowanceRate()).isEqualByComparingTo("10.00");
        assertThat(record.getDeputationAllowanceCap()).isEqualByComparingTo("9000.00");
    }

    @Test
    void initiateDeputation_foreignPostPayScale_getsNoDeputationAllowance() {
        DeputationInitiateRequest request = new DeputationInitiateRequest(1L, DeputationDirection.DEPUTATION_OUT, "World Bank", "INTERNATIONAL",
                "Washington", false, LocalDate.now(), LocalDate.now().plusYears(2), PayOption.FOREIGN_POST_PAY_SCALE,
                false, null, null);

        EmployeeDeputationRecord record = lifecycleService.initiateDeputation(request);

        assertThat(record.getDeputationAllowanceRate()).isEqualByComparingTo("0.00");
        assertThat(record.getDeputationAllowanceCap()).isEqualByComparingTo("0.00");
    }

    @Test
    void initiateDeputation_alreadyActive_throws() {
        when(deputationRepository.findByEmployee_IdAndStatus(1L, DeputationStatus.ACTIVE))
                .thenReturn(Optional.of(new EmployeeDeputationRecord(employee, DeputationDirection.DEPUTATION_OUT, "NTPC", "PSU",
                        "Delhi", true, LocalDate.now(), LocalDate.now().plusYears(1), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null)));

        assertThatThrownBy(() -> lifecycleService.initiateDeputation(new DeputationInitiateRequest(1L, DeputationDirection.DEPUTATION_OUT,
                "SAIL", "PSU", "Kolkata", false, LocalDate.now(), LocalDate.now().plusYears(1), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, true, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void processRepatriation_marksRecordRepatriated() {
        EmployeeDeputationRecord record = new EmployeeDeputationRecord(employee, DeputationDirection.DEPUTATION_OUT, "NTPC", "PSU",
                "Delhi", true, LocalDate.now().minusYears(1), LocalDate.now(), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        ReflectionTestUtils.setField(record, "id", 700L);
        when(deputationRepository.findById(700L)).thenReturn(Optional.of(record));

        EmployeeDeputationRecord updated = lifecycleService.processRepatriation(700L,
                new DeputationRepatriationRequest("REPAT/1", LocalDate.now(), "tenure complete"));

        assertThat(updated.getStatus()).isEqualTo(DeputationStatus.REPATRIATED);
        assertThat(updated.getRepatriationOrderNo()).isEqualTo("REPAT/1");
    }

    // ---- PayrollBatchComputationService deputation hook (full-service integration-style test) ----

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository;
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
    @Mock private EmployeeSuspensionRecordRepository suspensionRecordRepository;
    @Mock private EmployeeSuspensionNecRepository suspensionNecRepository;
    @Mock private in.gov.jci.hrms.service.PayrollQueryService payrollQueryService;
    @Mock private CpfLoanPayrollRecoveryResolverService cpfLoanPayrollRecoveryResolverService;
    @Mock private JciEccsPayrollRecoveryResolverService jciEccsPayrollRecoveryResolverService;

    private PayrollBatchComputationService payrollBatchService;
    private PayrollBatch batch;
    private RegularPayFixation fixation;

    private void setUpPayrollBatchService() {
        payrollBatchService = new PayrollBatchComputationService(payrollBatchRepository, payrollMonthlyRecordRepository,
                payrollMonthlyHeadItemRepository, payrollMonthlyStatutoryItemRepository, employeeRepository, regularPayFixationRepository,
                daRateHistoryRepository, dailyAttendanceRepository, payrollHraRateRepository, transportAllowanceRateRepository,
                ptaxSlabRepository, stateMasterRepository, payrollStatutoryParameterRepository, npsDeclarationRepository,
                vehicleAllotmentRepository, quarterAllotmentService, encashmentRepository, payrollMovementInputRepository,
                payrollTdsEngine, payrollTaxOverrideRepository, ceaClaimRepository, suspensionRecordRepository, suspensionNecRepository,
                deputationRepository, payrollQueryService, cpfLoanPayrollRecoveryResolverService, jciEccsPayrollRecoveryResolverService);
        when(cpfLoanPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(CpfLoanPayrollRecoveryResolverService.RecoveryAmounts.ZERO);
        when(jciEccsPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(JciEccsPayrollRecoveryResolverService.RecoveryAmounts.ZERO);

        batch = new PayrollBatch("BATCH-2026-08", 8, 2026, "2026-2027");
        ReflectionTestUtils.setField(batch, "id", 100L);
        batch.setStatus(PayrollBatchStatus.DRAFT);

        LocalDate periodStart = LocalDate.of(2026, 8, 1);
        LocalDate periodEnd = LocalDate.of(2026, 8, 31);

        fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("70000.00"), LocalDate.of(2018, 1, 1));

        when(payrollBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(payrollMonthlyRecordRepository.deleteByBatch_Id(100L)).thenReturn(0L);
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee));
        when(employeeRepository.findByStatus(EmployeeStatus.SUSPENDED)).thenReturn(List.of());
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(fixation));
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(ScaleType.IDA, periodEnd))
                .thenReturn(Optional.of(new in.gov.jci.hrms.entity.DaRateHistory(ScaleType.IDA, LocalDate.of(2020, 4, 1), new BigDecimal("17.00"), true)));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L, periodStart, periodEnd)).thenReturn(List.of());
        when(quarterAllotmentService.isHraSuppressed(1L, periodStart, periodEnd)).thenReturn(false);
        when(quarterAllotmentService.findActiveOccupancy(1L, periodStart, periodEnd)).thenReturn(Optional.empty());
        when(payrollHraRateRepository.findActiveOn(periodEnd)).thenReturn(List.of(
                new in.gov.jci.hrms.entity.PayrollHraRate("Z", new BigDecimal("10.00"), BigDecimal.ZERO, LocalDate.of(2024, 1, 1), null, null)));
        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(any(), anyString())).thenReturn(List.of());
        when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(any(), any())).thenReturn(List.of());
        when(npsDeclarationRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull(anyString())).thenReturn(Optional.empty());
        when(encashmentRepository.findEligibleForPayrollBatch(any(), any())).thenReturn(List.of());
        when(encashmentRepository.findByPayrollBatch_IdAndPayrollProcessedFalse(100L)).thenReturn(List.of());
        when(payrollTdsEngine.computeMonthlyTds(any(), anyString(), anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PayrollTdsEngine.TdsResult(BigDecimal.ZERO, BigDecimal.ZERO, false, null, 8, BigDecimal.ZERO, BigDecimal.ZERO));
        when(payrollMonthlyRecordRepository.saveAndFlush(any(PayrollMonthlyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(payrollMonthlyHeadItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> amountForHead(int headCount) {
        return capturedHeadItems().stream().filter(i -> i.getHeadCount() == headCount).map(PayrollMonthlyHeadItem::getAmount).findFirst();
    }

    @Test
    void processBatch_activeDeputationDifferentStation_computesHead67CappedAtNineThousand() {
        setUpPayrollBatchService();
        EmployeeDeputationRecord deputation = new EmployeeDeputationRecord(employee, DeputationDirection.DEPUTATION_OUT, "SAIL", "PSU",
                "Kolkata", false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        deputation.setDeputationAllowanceRate(new BigDecimal("10.00"));
        deputation.setDeputationAllowanceCap(new BigDecimal("9000.00"));
        when(deputationRepository.findByEmployee_IdAndStatus(1L, DeputationStatus.ACTIVE)).thenReturn(Optional.of(deputation));

        payrollBatchService.processBatch(100L);

        // Basic 70000 * 1.17 (17% DA) = 81900; 10% of that = 8190, below the 9000 cap - Head 67 = 8190.
        assertThat(amountForHead(HEAD_DEP_ALLOW)).isPresent();
        assertThat(amountForHead(HEAD_DEP_ALLOW).get()).isEqualByComparingTo("8190");
    }

    @Test
    void processBatch_deputationAllowanceExceedingCap_isCappedAtNineThousand() {
        setUpPayrollBatchService();
        fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("100000.00"), LocalDate.of(2018, 1, 1));
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(fixation));

        EmployeeDeputationRecord deputation = new EmployeeDeputationRecord(employee, DeputationDirection.DEPUTATION_OUT, "SAIL", "PSU",
                "Kolkata", false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        deputation.setDeputationAllowanceRate(new BigDecimal("10.00"));
        deputation.setDeputationAllowanceCap(new BigDecimal("9000.00"));
        when(deputationRepository.findByEmployee_IdAndStatus(1L, DeputationStatus.ACTIVE)).thenReturn(Optional.of(deputation));

        payrollBatchService.processBatch(100L);

        // Basic 100000 * 1.17 = 117000; 10% = 11700, which exceeds the 9000 cap - Head 67 must clamp to 9000.
        assertThat(amountForHead(HEAD_DEP_ALLOW)).isPresent();
        assertThat(amountForHead(HEAD_DEP_ALLOW).get()).isEqualByComparingTo("9000");
    }

    @Test
    void processBatch_foreignPostPayScaleDeputation_getsNoHead67() {
        setUpPayrollBatchService();
        EmployeeDeputationRecord deputation = new EmployeeDeputationRecord(employee, DeputationDirection.DEPUTATION_OUT, "World Bank",
                "INTERNATIONAL", "Washington", false, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), PayOption.FOREIGN_POST_PAY_SCALE, null);
        when(deputationRepository.findByEmployee_IdAndStatus(1L, DeputationStatus.ACTIVE)).thenReturn(Optional.of(deputation));

        payrollBatchService.processBatch(100L);

        assertThat(amountForHead(HEAD_DEP_ALLOW)).isEmpty();
    }
}
