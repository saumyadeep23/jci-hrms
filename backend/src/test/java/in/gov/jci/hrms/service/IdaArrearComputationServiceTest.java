package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeIdaArrearBreakup;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.PredecessorPayrollUnfinalizedException;
import in.gov.jci.hrms.repository.EmployeeIdaArrearBreakupRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdaArrearComputationServiceTest {

    private static final int HEAD_ARR_DA = 14;
    private static final int HEAD_ARR_CPF = 29;
    private static final int HEAD_ARR_NPS = 62;
    private static final int STAT_HEAD_ARR_CPF = 11;
    private static final int STAT_HEAD_ARR_JCPF = 12;
    private static final int STAT_HEAD_ARR_E_NPS = 14;
    private static final int STAT_HEAD_ARR_J_NPS = 15;

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollMonthlyHeadItemRepository headItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    @Mock private EmployeeIdaArrearBreakupRepository arrearBreakupRepository;
    @Mock private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock private PayrollComputationService payrollComputationService;

    private IdaArrearComputationService service;

    private Employee cpfEmployee;
    private Employee npsEmployee;
    private DaRateHistory daOrder;
    private PayrollBatch currentBatch;
    private PayrollMonthlyRecord currentRecord;

    @BeforeEach
    void setUp() {
        service = new IdaArrearComputationService(payrollBatchRepository, payrollRunRepository, payrollMonthlyRecordRepository,
                headItemRepository, statutoryItemRepository, payrollStatutoryParameterRepository, arrearBreakupRepository,
                encashmentRepository, payrollComputationService);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");

        cpfEmployee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(cpfEmployee, "id", 1L);

        npsEmployee = new Employee("EMP-002", "Vikram", "Singh", "vikram.singh@example.com", LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(npsEmployee, "id", 2L);
        npsEmployee.setNpsEligible(true);
        npsEmployee.setPranNumber("PRAN-0002");

        daOrder = new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 7, 1), new BigDecimal("55.70"), true);
        ReflectionTestUtils.setField(daOrder, "id", 900L);
        when(payrollComputationService.resolveDaPercentage(ScaleType.IDA, LocalDate.of(2026, 6, 30)))
                .thenReturn(new BigDecimal("54.10"));

        currentBatch = new PayrollBatch("BATCH-2026-08", 8, 2026, "2026-2027");
        ReflectionTestUtils.setField(currentBatch, "id", 200L);
        currentBatch.setStatus(PayrollBatchStatus.DRAFT);

        when(payrollRunRepository.findByCycleYearAndCycleMonth(2026, 8)).thenReturn(Optional.empty());
        when(payrollComputationService.deriveCycleDates(2026, 8))
                .thenReturn(new PayrollComputationService.CycleDates(LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)));
        when(payrollRunRepository.saveAndFlush(any(PayrollRun.class))).thenAnswer(inv -> inv.getArgument(0));

        when(payrollStatutoryParameterRepository.findActiveParamOnDate(any(), any())).thenReturn(Optional.empty());
        when(arrearBreakupRepository.save(any(EmployeeIdaArrearBreakup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(encashmentRepository.findSettledInWindow(any(), any(), any())).thenReturn(List.of());
    }

    private void stubJulyRetroMonth(Employee employee, Long batchId) {
        PayrollBatch julyBatch = new PayrollBatch("BATCH-2026-07", 7, 2026, "2026-2027");
        ReflectionTestUtils.setField(julyBatch, "id", batchId);
        julyBatch.setStatus(PayrollBatchStatus.DISBURSED);
        when(payrollBatchRepository.findBySalMonthAndSalYear(7, 2026)).thenReturn(Optional.of(julyBatch));

        PayrollMonthlyRecord julyRecord = new PayrollMonthlyRecord(julyBatch, employee, employee.getEmployeeCode(), 7, 2026,
                "HO", "Manager", "Z", "IDA", 31);
        julyRecord.setBasicPay(new BigDecimal("50000.00"));
        julyRecord.setDaysPresent(new BigDecimal("31.0"));
        when(payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(batchId, employee.getId())).thenReturn(Optional.of(julyRecord));
        when(arrearBreakupRepository.findByEmployee_IdAndDaRateHistory_IdAndRetroMonthAndRetroYear(employee.getId(), 900L, 7, 2026))
                .thenReturn(Optional.empty());
    }

    private void stubCurrentRecord(Employee employee) {
        currentRecord = new PayrollMonthlyRecord(currentBatch, employee, employee.getEmployeeCode(), 8, 2026,
                "HO", "Manager", "Z", "IDA", 31);
        when(payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(200L, employee.getId())).thenReturn(Optional.of(currentRecord));
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(headItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private List<PayrollMonthlyStatutoryItem> capturedStatutoryItems() {
        ArgumentCaptor<PayrollMonthlyStatutoryItem> captor = ArgumentCaptor.forClass(PayrollMonthlyStatutoryItem.class);
        verify(statutoryItemRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Optional<BigDecimal> headAmount(int headCount) {
        return capturedHeadItems().stream().filter(i -> i.getHeadCount() == headCount).map(PayrollMonthlyHeadItem::getAmount).findFirst();
    }

    private Optional<BigDecimal> statAmount(int statHeadCount) {
        return capturedStatutoryItems().stream().filter(i -> i.getStatHeadCount() == statHeadCount).map(PayrollMonthlyStatutoryItem::getAmount).findFirst();
    }

    // ---- Predecessor gate ----

    @Test
    void validatePredecessorGate_antecedentMonthNotDisbursed_throws() {
        PayrollBatch julyBatch = new PayrollBatch("BATCH-2026-07", 7, 2026, "2026-2027");
        julyBatch.setStatus(PayrollBatchStatus.HR_FINALIZED); // in progress, not yet DISBURSED
        when(payrollBatchRepository.findBySalMonthAndSalYear(7, 2026)).thenReturn(Optional.of(julyBatch));

        assertThatThrownBy(() -> service.validatePredecessorGate(8, 2026, LocalDate.of(2026, 7, 1)))
                .isInstanceOf(PredecessorPayrollUnfinalizedException.class);
    }

    @Test
    void validatePredecessorGate_antecedentMonthMissingEntirely_throws() {
        when(payrollBatchRepository.findBySalMonthAndSalYear(7, 2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validatePredecessorGate(8, 2026, LocalDate.of(2026, 7, 1)))
                .isInstanceOf(PredecessorPayrollUnfinalizedException.class);
    }

    @Test
    void validatePredecessorGate_everyRetroMonthDisbursed_passes() {
        PayrollBatch julyBatch = new PayrollBatch("BATCH-2026-07", 7, 2026, "2026-2027");
        julyBatch.setStatus(PayrollBatchStatus.DISBURSED);
        when(payrollBatchRepository.findBySalMonthAndSalYear(7, 2026)).thenReturn(Optional.of(julyBatch));

        service.validatePredecessorGate(8, 2026, LocalDate.of(2026, 7, 1));
        // no exception
    }

    // ---- Head/stat mapping ----

    @Test
    void materializeRealizedArrears_cpfScheme_postsHead14And29AndStat11And12() {
        stubJulyRetroMonth(cpfEmployee, 50L);
        stubCurrentRecord(cpfEmployee);

        IdaArrearComputationService.RealizedArrearResult result = service.materializeRealizedArrears(cpfEmployee, currentBatch, daOrder);

        // gross = 50000 * (55.70-54.10)/100 * 31/31 = 800.00; CPF 12% both sides = 96.00
        assertThat(result.totalGrossArrear()).isEqualByComparingTo("800.00");
        assertThat(headAmount(HEAD_ARR_DA)).contains(new BigDecimal("800.00"));
        assertThat(headAmount(HEAD_ARR_CPF)).contains(new BigDecimal("96.00"));
        assertThat(headAmount(HEAD_ARR_NPS)).isEmpty();
        assertThat(statAmount(STAT_HEAD_ARR_CPF)).contains(new BigDecimal("96.00"));
        assertThat(statAmount(STAT_HEAD_ARR_JCPF)).contains(new BigDecimal("96.00"));
        assertThat(statAmount(STAT_HEAD_ARR_E_NPS)).isEmpty();
        assertThat(statAmount(STAT_HEAD_ARR_J_NPS)).isEmpty();
    }

    @Test
    void materializeRealizedArrears_npsScheme_postsHead14And62AndStat14And15() {
        stubJulyRetroMonth(npsEmployee, 51L);
        stubCurrentRecord(npsEmployee);

        IdaArrearComputationService.RealizedArrearResult result = service.materializeRealizedArrears(npsEmployee, currentBatch, daOrder);

        // gross = 800.00; NPS 10% both sides = 80.00
        assertThat(result.totalGrossArrear()).isEqualByComparingTo("800.00");
        assertThat(headAmount(HEAD_ARR_DA)).contains(new BigDecimal("800.00"));
        assertThat(headAmount(HEAD_ARR_NPS)).contains(new BigDecimal("80.00"));
        assertThat(headAmount(HEAD_ARR_CPF)).isEmpty();
        assertThat(statAmount(STAT_HEAD_ARR_E_NPS)).contains(new BigDecimal("80.00"));
        assertThat(statAmount(STAT_HEAD_ARR_J_NPS)).contains(new BigDecimal("80.00"));
        assertThat(statAmount(STAT_HEAD_ARR_CPF)).isEmpty();
        assertThat(statAmount(STAT_HEAD_ARR_JCPF)).isEmpty();
    }

    @Test
    void materializeRealizedArrears_missingCurrentBatchRecord_throws() {
        stubJulyRetroMonth(cpfEmployee, 50L);
        when(payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(200L, cpfEmployee.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materializeRealizedArrears(cpfEmployee, currentBatch, daOrder))
                .isInstanceOf(in.gov.jci.hrms.exception.BusinessRuleViolationException.class);
    }
}
