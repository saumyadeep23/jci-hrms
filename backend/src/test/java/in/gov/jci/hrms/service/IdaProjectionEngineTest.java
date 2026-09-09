package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DaProjectionBatch;
import in.gov.jci.hrms.entity.DaProjectionMonthlyBreakup;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.repository.DaProjectionBatchRepository;
import in.gov.jci.hrms.repository.DaProjectionEmployeeRepository;
import in.gov.jci.hrms.repository.DaProjectionMonthlyBreakupRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdaProjectionEngineTest {

    @Mock private DaProjectionBatchRepository batchRepository;
    @Mock private DaProjectionEmployeeRepository projectionEmployeeRepository;
    @Mock private DaProjectionMonthlyBreakupRepository monthlyBreakupRepository;
    @Mock private DaRateHistoryRepository daRateHistoryRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    @Mock private LeaveEncashmentApplicationRepository encashmentRepository;

    private IdaProjectionEngineService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new IdaProjectionEngineService(batchRepository, projectionEmployeeRepository, monthlyBreakupRepository,
                daRateHistoryRepository, employeeRepository, regularPayFixationRepository, payrollBatchRepository,
                payrollMonthlyRecordRepository, payrollStatutoryParameterRepository, encashmentRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setStatus(EmployeeStatus.ACTIVE);
        // Not NPS-eligible / no PRAN => CPF scheme (see IdaProjectionEngineService.pensionScheme()).

        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee));
        when(batchRepository.saveAndFlush(any(DaProjectionBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectionEmployeeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(monthlyBreakupRepository.save(any(DaProjectionMonthlyBreakup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(encashmentRepository.findSettledInWindow(any(), any(), any())).thenReturn(List.of());
        when(payrollStatutoryParameterRepository.findActiveParamOnDate(any(), any())).thenReturn(Optional.empty());
    }

    private List<DaProjectionMonthlyBreakup> capturedBreakups() {
        ArgumentCaptor<DaProjectionMonthlyBreakup> captor = ArgumentCaptor.forClass(DaProjectionMonthlyBreakup.class);
        verify(monthlyBreakupRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void executeSimulation_retroMonthWithLwp_proratesDeltaDaByPaidDays() {
        // Order effective 01-Jul-2026, drawal in Aug-2026 => exactly one retro month: Jul-2026.
        LocalDate effectiveFrom = LocalDate.of(2026, 7, 1);
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, effectiveFrom.minusDays(1)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2020, 4, 1), new BigDecimal("54.10"), true)));

        PayrollBatch julyBatch = new PayrollBatch("BATCH-2026-07", 7, 2026, "2026-2027");
        ReflectionTestUtils.setField(julyBatch, "id", 50L);
        julyBatch.setStatus(PayrollBatchStatus.DISBURSED);
        when(payrollBatchRepository.findBySalMonthAndSalYear(7, 2026)).thenReturn(Optional.of(julyBatch));

        PayrollMonthlyRecord record = new PayrollMonthlyRecord(julyBatch, employee, "EMP-001", 7, 2026,
                "HO", "Manager", "Z", "IDA", 31);
        record.setBasicPay(new BigDecimal("50000.00"));
        record.setDaysPresent(new BigDecimal("28.0")); // 3 days LWP out of 31
        when(payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(50L, 1L)).thenReturn(Optional.of(record));

        service.executeSimulation(ScaleType.IDA, new BigDecimal("55.70"), effectiveFrom, 8, 2026, null);

        List<DaProjectionMonthlyBreakup> breakups = capturedBreakups();
        assertThat(breakups).hasSize(1);
        DaProjectionMonthlyBreakup julyBreakup = breakups.get(0);
        assertThat(julyBreakup.getSalMonth()).isEqualTo(7);
        assertThat(julyBreakup.getTotalDays()).isEqualTo(31);
        assertThat(julyBreakup.getPaidDays()).isEqualByComparingTo("28.0");
        // deltaDa = 50000 * (55.70-54.10)/100 * 28/31 = 800 * 28/31 = 722.58 (HALF_UP to 2dp)
        assertThat(julyBreakup.getDeltaDa()).isEqualByComparingTo("722.58");
        // CPF scheme: employee 12% of delta, employer (JCPF) 12% of delta.
        assertThat(julyBreakup.getEmployeeCpfArrear()).isEqualByComparingTo("86.71");
        assertThat(julyBreakup.getEmployerJcpfArrear()).isEqualByComparingTo("86.71");
        assertThat(julyBreakup.getEmployeeNpsArrear()).isEqualByComparingTo("0.00");
    }

    @Test
    void executeSimulation_openCurrentMonth_usesCurrentBasicAndAssumesFullMonthPaid() {
        // Order effective 01-Aug-2026, drawal in Aug-2026 => zero retro months, but the projection window
        // (effectiveFrom..drawal-1) is empty here; use a same-month-as-open-current-month scenario instead:
        // effectiveFrom in the PAST with drawal this same open month, and no payroll_batches row exists for
        // it yet (not yet DISBURSED) - falls back to current RegularPayFixation basic, full days paid.
        LocalDate effectiveFrom = LocalDate.of(2026, 8, 1);
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, effectiveFrom.minusDays(1)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2020, 4, 1), new BigDecimal("54.10"), true)));

        when(payrollBatchRepository.findBySalMonthAndSalYear(8, 2026)).thenReturn(Optional.empty());
        RegularPayFixation fixation = new RegularPayFixation(employee, null, new BigDecimal("60000.00"), LocalDate.of(2020, 1, 1));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));

        // Drawal in September => one retro month: August (the still-open current month).
        service.executeSimulation(ScaleType.IDA, new BigDecimal("55.70"), effectiveFrom, 9, 2026, null);

        List<DaProjectionMonthlyBreakup> breakups = capturedBreakups();
        assertThat(breakups).hasSize(1);
        DaProjectionMonthlyBreakup augBreakup = breakups.get(0);
        assertThat(augBreakup.getTotalDays()).isEqualTo(31);
        assertThat(augBreakup.getPaidDays()).isEqualByComparingTo("31");
        assertThat(augBreakup.getActualBasicPay()).isEqualByComparingTo("60000.00");
        // deltaDa = 60000 * 1.60/100 * 31/31 = 960.00
        assertThat(augBreakup.getDeltaDa()).isEqualByComparingTo("960.00");
    }
}
