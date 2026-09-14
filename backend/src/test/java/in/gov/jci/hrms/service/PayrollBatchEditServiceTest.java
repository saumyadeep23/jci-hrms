package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EditPayrollLineDto;
import in.gov.jci.hrms.dto.PayrollEditResponse;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollEditLogRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.SalaryHeadRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.StatutoryHeadRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the CPF(27)/EPF(stat 1)/Pension(stat 4)/JCPF(stat 3) cascade that PayrollBatchEditService
 * mirrors from PayrollBatchComputationService.resolveEmployerContributions() - EPF always equals the
 * edited CPF amount, Pension is the EPS carve-out, and JCPF is unconditionally EPF minus Pension. Also
 * covers whole-rupee rounding of edited amounts. Basic+DA of 58500 and its known EPS figures (CPF
 * 7020, pension 1250, JCPF 5770) are reused from PayrollBatchComputationServiceTest for cross-checking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollBatchEditServiceTest {

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_DA_IDA = 8;
    private static final int HEAD_HRA = 9;
    private static final int HEAD_CPF = 27;
    private static final int STAT_HEAD_EMPLOYER_EPF = 1;
    private static final int STAT_HEAD_EMPLOYER_JCPF = 3;
    private static final int STAT_HEAD_EMPLOYER_PENSION = 4;

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository recordRepository;
    @Mock private PayrollMonthlyHeadItemRepository headItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    @Mock private PayrollEditLogRepository editLogRepository;
    @Mock private SalaryHeadRepository salaryHeadRepository;
    @Mock private StatutoryHeadRepository statutoryHeadRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private DaRateHistoryRepository daRateHistoryRepository;
    @Mock private PayrollHraRateRepository payrollHraRateRepository;
    @Mock private PtaxSlabRepository ptaxSlabRepository;
    @Mock private StateMasterRepository stateMasterRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    @Mock private EmployeeRepository employeeRepository;

    private PayrollBatchEditService service;

    private PayrollBatch batch;
    private Employee employee;
    private RegularPayFixation fixation;
    private PayrollMonthlyRecord record;

    @BeforeEach
    void setUp() {
        service = new PayrollBatchEditService(payrollBatchRepository, recordRepository, headItemRepository,
                statutoryItemRepository, editLogRepository, salaryHeadRepository, statutoryHeadRepository,
                regularPayFixationRepository, daRateHistoryRepository, payrollHraRateRepository, ptaxSlabRepository,
                stateMasterRepository, payrollStatutoryParameterRepository, employeeRepository);

        batch = new PayrollBatch("BATCH-2026-08", 8, 2026, "2026-2027");
        ReflectionTestUtils.setField(batch, "id", 100L);
        batch.setStatus(PayrollBatchStatus.CALCULATED);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        GradeScaleMaster gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false,
                new BigDecimal("50000.00"), new BigDecimal("80000.00"));
        ReflectionTestUtils.setField(gradeScale, "id", 5L);
        gradeScale.setScaleType(ScaleType.IDA);
        fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2020, 1, 15));

        record = new PayrollMonthlyRecord(batch, employee, "EMP-001", 8, 2026, null, null, CityClass.Z.name(), "IDA", 31);
        ReflectionTestUtils.setField(record, "tranId", 500L);
        record.setBasicPay(new BigDecimal("50000"));

        when(payrollBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
        when(recordRepository.findById(500L)).thenReturn(Optional.of(record));
        when(recordRepository.saveAndFlush(any(PayrollMonthlyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salaryHeadRepository.findAllByOrderByHeadCountAsc()).thenReturn(List.of());
        when(statutoryHeadRepository.findAllByOrderByStatHeadCountAsc()).thenReturn(List.of());
        when(headItemRepository.findByRecord_TranId(500L)).thenReturn(List.of());
        when(statutoryItemRepository.findByRecord_TranId(500L)).thenReturn(List.of());
        when(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull(any())).thenReturn(Optional.empty());
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(headItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> amountForHead(int headCount) {
        return capturedHeadItems().stream()
                .filter(item -> item.getHeadCount() == headCount)
                .map(PayrollMonthlyHeadItem::getAmount)
                .findFirst();
    }

    private List<PayrollMonthlyStatutoryItem> capturedStatutoryItems() {
        ArgumentCaptor<PayrollMonthlyStatutoryItem> captor = ArgumentCaptor.forClass(PayrollMonthlyStatutoryItem.class);
        verify(statutoryItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> amountForStatHead(int statHeadCount) {
        return capturedStatutoryItems().stream()
                .filter(item -> item.getStatHeadCount() == statHeadCount)
                .map(PayrollMonthlyStatutoryItem::getAmount)
                .findFirst();
    }

    private PayrollHraRate hraRate(CityClass cityClass, String percentage) {
        return new PayrollHraRate(cityClass.name(), new BigDecimal(percentage), BigDecimal.ZERO, LocalDate.of(2024, 1, 1), null, null);
    }

    @Test
    void editSalaryHead_basicPayEdit_epsEligible_cascadesEpfPensionAndJcpf() {
        employee.setEpsEligible(true);
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 31)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));
        when(payrollHraRateRepository.findActiveOn(LocalDate.of(2026, 8, 31))).thenReturn(List.of(hraRate(CityClass.Z, "10.00")));

        service.editSalaryHead(100L, 500L, new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("50000"), "Correction"), null);

        // Basic 50000 + DA (17% of 50000 = 8500) = basicPlusDa 58500 - same figures as
        // PayrollBatchComputationServiceTest's EPS-eligible scenario.
        assertThat(amountForHead(HEAD_CPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).contains(new BigDecimal("1250"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("5770"));
    }

    @Test
    void editSalaryHead_basicPayEdit_notEpsEligible_postsFullCpfToJcpfWithNoPension() {
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 31)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));
        when(payrollHraRateRepository.findActiveOn(LocalDate.of(2026, 8, 31))).thenReturn(List.of(hraRate(CityClass.Z, "10.00")));

        service.editSalaryHead(100L, 500L, new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("50000"), "Correction"), null);

        assertThat(amountForHead(HEAD_CPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).isEmpty();
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("7020"));
    }

    @Test
    void editSalaryHead_directCpfEdit_cascadesToEpfPensionAndJcpf() {
        employee.setEpsEligible(true);
        record.setBasicPay(new BigDecimal("58500"));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));

        service.editSalaryHead(100L, 500L, new EditPayrollLineDto(HEAD_CPF, new BigDecimal("7020"), "Officer override"), null);

        assertThat(amountForHead(HEAD_CPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_EPF)).contains(new BigDecimal("7020"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_PENSION)).contains(new BigDecimal("1250"));
        assertThat(amountForStatHead(STAT_HEAD_EMPLOYER_JCPF)).contains(new BigDecimal("5770"));
    }

    @Test
    void editSalaryHead_directCpfEdit_unrelatedToBasicEdit_doesNotRequireDaOrHraMasters() {
        // Editing Head 27 directly must not touch DA/HRA rate masters - only Basic Pay edits cascade to those.
        record.setBasicPay(new BigDecimal("58500"));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));

        service.editSalaryHead(100L, 500L, new EditPayrollLineDto(HEAD_CPF, new BigDecimal("7020"), "Officer override"), null);

        assertThat(amountForHead(HEAD_HRA)).isEmpty();
        verify(payrollHraRateRepository, org.mockito.Mockito.never()).findActiveOn(any());
        verify(daRateHistoryRepository, org.mockito.Mockito.never())
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any());
    }

    @Test
    void editSalaryHead_nonIntegerAmount_isRoundedToWholeRupee() {
        service.editSalaryHead(100L, 500L, new EditPayrollLineDto(HEAD_HRA, new BigDecimal("1234.60"), "Manual correction"), null);

        assertThat(amountForHead(HEAD_HRA)).contains(new BigDecimal("1235"));
    }

    @Test
    void previewSalaryHead_doesNotPersistAnything() {
        PayrollEditResponse response = service.previewSalaryHead(100L, 500L,
                new EditPayrollLineDto(HEAD_HRA, new BigDecimal("6000"), ""));

        assertThat(response.changedHeads()).hasSize(1);
        verify(headItemRepository, org.mockito.Mockito.never()).save(any());
        verify(recordRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }
}
