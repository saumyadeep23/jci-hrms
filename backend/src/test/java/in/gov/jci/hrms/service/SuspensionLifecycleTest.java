package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.SubsistenceReviewRequest;
import in.gov.jci.hrms.dto.SuspensionInitiateRequest;
import in.gov.jci.hrms.dto.SuspensionRevocationRequest;
import in.gov.jci.hrms.dto.SuspensionRevocationResponse;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuspensionNec;
import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.RegularizationType;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.SuspensionStatus;
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
class SuspensionLifecycleTest {

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_CPF = 27;
    private static final int HEAD_SUBSIST_ALLOW = 66;

    // ---- SuspensionLifecycleService unit tests ----

    @Mock private EmployeeSuspensionRecordRepository suspensionRepository;
    @Mock private EmployeeSuspensionNecRepository necRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private PayrollComputationService payrollComputationService;
    @Mock private PayrollMonthlyHeadItemRepository headItemRepository;

    private SuspensionLifecycleService lifecycleService;
    private Employee employee;
    private GradeScaleMaster gradeScale;

    @BeforeEach
    void setUp() {
        lifecycleService = new SuspensionLifecycleService(suspensionRepository, necRepository, employeeRepository,
                regularPayFixationRepository, payrollComputationService, headItemRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setStatus(EmployeeStatus.ACTIVE);

        gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false, new BigDecimal("50000.00"), new BigDecimal("80000.00"));
        gradeScale.setScaleType(ScaleType.IDA);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(suspensionRepository.save(any(EmployeeSuspensionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(necRepository.save(any(EmployeeSuspensionNec.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initiateSuspension_setsEmployeeStatusSuspended() {
        SuspensionInitiateRequest request = new SuspensionInitiateRequest(1L, "ORD/2026/1", LocalDate.now(), LocalDate.now(), "Delhi HQ");

        EmployeeSuspensionRecord record = lifecycleService.initiateSuspension(request);

        assertThat(record.getStatus()).isEqualTo(SuspensionStatus.UNDER_SUSPENSION);
        assertThat(record.getCurrentSubsistencePercentage()).isEqualByComparingTo("50.00");
        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.SUSPENDED);
    }

    @Test
    void initiateSuspension_alreadyUnderSuspension_throws() {
        when(suspensionRepository.findByEmployee_IdAndStatus(1L, SuspensionStatus.UNDER_SUSPENSION))
                .thenReturn(Optional.of(new EmployeeSuspensionRecord(employee, "OLD/1", LocalDate.now(), LocalDate.now(), "Delhi", null)));

        assertThatThrownBy(() -> lifecycleService.initiateSuspension(
                new SuspensionInitiateRequest(1L, "ORD/2026/2", LocalDate.now(), LocalDate.now(), "Delhi HQ")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reviewSubsistenceAllowance_beforeNinetyDays_throws() {
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(employee, "ORD/1", LocalDate.now(), LocalDate.now().minusDays(30), "Delhi", null);
        ReflectionTestUtils.setField(suspension, "id", 900L);
        when(suspensionRepository.findById(900L)).thenReturn(Optional.of(suspension));

        assertThatThrownBy(() -> lifecycleService.reviewSubsistenceAllowance(900L, new SubsistenceReviewRequest(false, "REV/1", "ok")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reviewSubsistenceAllowance_delayNotAttributableToEmployee_revisesTo75Percent() {
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(employee, "ORD/1", LocalDate.now(), LocalDate.now().minusDays(100), "Delhi", null);
        ReflectionTestUtils.setField(suspension, "id", 901L);
        when(suspensionRepository.findById(901L)).thenReturn(Optional.of(suspension));

        EmployeeSuspensionRecord updated = lifecycleService.reviewSubsistenceAllowance(901L, new SubsistenceReviewRequest(false, "REV/1", "delay not attributable"));

        assertThat(updated.getCurrentSubsistencePercentage()).isEqualByComparingTo("75.00");
    }

    @Test
    void reviewSubsistenceAllowance_delayAttributableToEmployee_reducesTo25Percent() {
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(employee, "ORD/1", LocalDate.now(), LocalDate.now().minusDays(100), "Delhi", null);
        ReflectionTestUtils.setField(suspension, "id", 902L);
        when(suspensionRepository.findById(902L)).thenReturn(Optional.of(suspension));

        EmployeeSuspensionRecord updated = lifecycleService.reviewSubsistenceAllowance(902L, new SubsistenceReviewRequest(true, "REV/2", "delay attributable"));

        assertThat(updated.getCurrentSubsistencePercentage()).isEqualByComparingTo("25.00");
    }

    @Test
    void revokeAndRegularize_reinstatesEmployeeAndComputesBackPayArrear() {
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);
        LocalDate revocationEffective = LocalDate.of(2026, 3, 31);
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(employee, "ORD/1", effectiveFrom, effectiveFrom, "Delhi", null);
        ReflectionTestUtils.setField(suspension, "id", 903L);
        employee.setStatus(EmployeeStatus.SUSPENDED);
        when(suspensionRepository.findById(903L)).thenReturn(Optional.of(suspension));

        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2018, 1, 1));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        when(payrollComputationService.resolveDaPercentage(ScaleType.IDA, revocationEffective)).thenReturn(new BigDecimal("20.00"));
        // Full pay/month = 50000 * 1.20 = 60000.00; 3 months (Jan-Mar) => 180000.00 total full pay.
        when(headItemRepository.sumAmountForEmployeeAndHeadInRange(1L, HEAD_SUBSIST_ALLOW, 1, 2026, 3, 2026))
                .thenReturn(new BigDecimal("90000.00")); // 50% subsistence actually paid across those 3 months

        SuspensionRevocationResponse response = lifecycleService.revokeAndRegularize(903L,
                new SuspensionRevocationRequest("REV-ORD/1", LocalDate.now(), revocationEffective, RegularizationType.REINSTATED, "reinstated"));

        assertThat(suspension.getStatus()).isEqualTo(SuspensionStatus.REVOKED);
        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(response.grossBackPayArrear()).isEqualByComparingTo("90000.00"); // 180000 - 90000
        assertThat(response.cpfDeductionOnArrear()).isEqualByComparingTo("10800.00"); // 12% of 90000
        assertThat(response.netArrearPayable()).isEqualByComparingTo("79200.00");
    }

    // ---- PayrollBatchComputationService suspension hook (full-service integration-style test) ----

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository;
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
    @Mock private EmployeeDeputationRecordRepository deputationRecordRepository;
    @Mock private in.gov.jci.hrms.service.PayrollQueryService payrollQueryService;
    @Mock private CpfLoanPayrollRecoveryResolverService cpfLoanPayrollRecoveryResolverService;
    @Mock private JciEccsPayrollRecoveryResolverService jciEccsPayrollRecoveryResolverService;

    private PayrollBatchComputationService payrollBatchService;
    private PayrollBatch batch;
    private EmployeeSuspensionRecord activeSuspension;

    private void setUpPayrollBatchService() {
        payrollBatchService = new PayrollBatchComputationService(payrollBatchRepository, payrollMonthlyRecordRepository,
                payrollMonthlyHeadItemRepository, payrollMonthlyStatutoryItemRepository, employeeRepository, regularPayFixationRepository,
                daRateHistoryRepository, dailyAttendanceRepository, payrollHraRateRepository, transportAllowanceRateRepository,
                ptaxSlabRepository, stateMasterRepository, payrollStatutoryParameterRepository, npsDeclarationRepository,
                vehicleAllotmentRepository, quarterAllotmentService, encashmentRepository, payrollMovementInputRepository,
                payrollTdsEngine, payrollTaxOverrideRepository, ceaClaimRepository, suspensionRepository, necRepository, deputationRecordRepository,
                payrollQueryService, cpfLoanPayrollRecoveryResolverService, jciEccsPayrollRecoveryResolverService);
        when(cpfLoanPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(CpfLoanPayrollRecoveryResolverService.RecoveryAmounts.ZERO);
        when(jciEccsPayrollRecoveryResolverService.resolve(any(), any())).thenReturn(JciEccsPayrollRecoveryResolverService.RecoveryAmounts.ZERO);

        batch = new PayrollBatch("BATCH-2026-08", 8, 2026, "2026-2027");
        ReflectionTestUtils.setField(batch, "id", 100L);
        batch.setStatus(PayrollBatchStatus.DRAFT);

        activeSuspension = new EmployeeSuspensionRecord(employee, "ORD/1", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), "Delhi", null);
        ReflectionTestUtils.setField(activeSuspension, "id", 900L);

        when(payrollBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(payrollMonthlyRecordRepository.deleteByBatch_Id(100L)).thenReturn(0L);
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of());
        when(employeeRepository.findByStatus(EmployeeStatus.SUSPENDED)).thenReturn(List.of(employee));
        when(suspensionRepository.findByEmployee_IdAndStatus(1L, SuspensionStatus.UNDER_SUSPENSION)).thenReturn(Optional.of(activeSuspension));
        when(payrollMonthlyRecordRepository.saveAndFlush(any(PayrollMonthlyRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.of(new in.gov.jci.hrms.entity.DaRateHistory(ScaleType.IDA, LocalDate.of(2020, 4, 1), new BigDecimal("17.00"), true)));
        when(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull(anyString())).thenReturn(Optional.empty());
        when(quarterAllotmentService.findActiveOccupancy(any(), any(), any())).thenReturn(Optional.empty());
        when(payrollTdsEngine.computeMonthlyTds(any(), anyString(), anyInt(), anyInt(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PayrollTdsEngine.TdsResult(BigDecimal.ZERO, BigDecimal.ZERO, false, null, 8, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(payrollMonthlyHeadItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void processBatch_necUnverified_holdsSalaryAndPostsNoHeadItems() {
        setUpPayrollBatchService();
        when(necRepository.findBySuspension_IdAndSalMonthAndSalYear(900L, 8, 2026)).thenReturn(Optional.empty());

        payrollBatchService.processBatch(100L);

        ArgumentCaptor<PayrollMonthlyRecord> recordCaptor = ArgumentCaptor.forClass(PayrollMonthlyRecord.class);
        verify(payrollMonthlyRecordRepository).saveAndFlush(recordCaptor.capture());
        assertThat(recordCaptor.getValue().isSalaryHeld()).isTrue();
        assertThat(recordCaptor.getValue().getGrossAmount()).isEqualByComparingTo("0.00");
        assertThat(capturedHeadItems()).isEmpty();
    }

    @Test
    void processBatch_necVerified_releasesHead66AndSuppressesCpf() {
        setUpPayrollBatchService();
        EmployeeSuspensionNec verifiedNec = new EmployeeSuspensionNec(activeSuspension, employee, 8, 2026);
        verifiedNec.setVerified(true);
        when(necRepository.findBySuspension_IdAndSalMonthAndSalYear(900L, 8, 2026)).thenReturn(Optional.of(verifiedNec));

        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2018, 1, 1));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));

        payrollBatchService.processBatch(100L);

        List<PayrollMonthlyHeadItem> headItems = capturedHeadItems();
        assertThat(headItems).anyMatch(i -> i.getHeadCount() == HEAD_SUBSIST_ALLOW);
        assertThat(headItems).noneMatch(i -> i.getHeadCount() == HEAD_CPF);
        assertThat(headItems).noneMatch(i -> i.getHeadCount() == HEAD_BASIC);
        // 50000 * 1.17 (17% DA) * 50% subsistence = 29250.00
        PayrollMonthlyHeadItem subsistItem = headItems.stream().filter(i -> i.getHeadCount() == HEAD_SUBSIST_ALLOW).findFirst().orElseThrow();
        assertThat(subsistItem.getAmount()).isEqualByComparingTo("29250.00");
    }
}
