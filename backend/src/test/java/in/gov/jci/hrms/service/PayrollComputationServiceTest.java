package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.PayrollRateProperties;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollComputationServiceTest {

    @Mock
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock
    private DaRateHistoryRepository daRateHistoryRepository;
    @Mock
    private EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    @Mock
    private RegularPayFixationRepository regularPayFixationRepository;

    private PayrollRateProperties rateProperties;
    private PayrollComputationService payrollComputationService;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        rateProperties = new PayrollRateProperties();
        rateProperties.setHraPercentX(new BigDecimal("24.00"));
        rateProperties.setHraPercentY(new BigDecimal("16.00"));
        rateProperties.setHraPercentZ(new BigDecimal("8.00"));
        rateProperties.setTransportAllowanceBase(new BigDecimal("1600.00"));

        payrollComputationService = new PayrollComputationService(dailyAttendanceRepository, daRateHistoryRepository,
                rateProperties, superannuationDetailsRepository, regularPayFixationRepository);

        department = new Department("ENG", "Engineering");
        designation = new Designation("Manager");
    }

    private GradeScaleMaster gradeScale(ScaleType scaleType, String minimumBasic) {
        GradeScaleMaster gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false,
                new BigDecimal(minimumBasic), new BigDecimal("80000.00"));
        gradeScale.setScaleType(scaleType);
        return gradeScale;
    }

    private Employee employeeWith(RegionalOffice regionalOffice, EmployeeStatus status) {
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        employee.setRegionalOffice(regionalOffice);
        employee.setStatus(status);
        return employee;
    }

    private RegionalOffice regionalOffice(CityClass cityClass) {
        return new RegionalOffice("RO-1", "Some RO", "State", cityClass, true);
    }

    // ---- deriveCycleDates ----

    @Test
    void deriveCycleDates_namesTheRunByItsClosingMonth() {
        PayrollComputationService.CycleDates dates = payrollComputationService.deriveCycleDates(2026, 8);

        assertThat(dates.startDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(dates.endDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void deriveCycleDates_acrossYearBoundary_rollsBackToPriorDecember() {
        PayrollComputationService.CycleDates dates = payrollComputationService.deriveCycleDates(2026, 1);

        assertThat(dates.startDate()).isEqualTo(LocalDate.of(2025, 12, 26));
        assertThat(dates.endDate()).isEqualTo(LocalDate.of(2026, 1, 25));
    }

    // ---- computeLopDays ----

    @Test
    void computeLopDays_countsAbsentAsFullDayAndHalfDayAsHalf_ignoresOtherStatuses() {
        DailyAttendance absent = attendanceOn(LocalDate.of(2026, 8, 1), AttendanceStatus.ABSENT);
        DailyAttendance halfDay = attendanceOn(LocalDate.of(2026, 8, 2), AttendanceStatus.HALF_DAY);
        DailyAttendance present = attendanceOn(LocalDate.of(2026, 8, 3), AttendanceStatus.PRESENT);
        DailyAttendance onLeave = attendanceOn(LocalDate.of(2026, 8, 4), AttendanceStatus.ON_LEAVE);

        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)))
                .thenReturn(List.of(absent, halfDay, present, onLeave));

        BigDecimal lopDays = payrollComputationService.computeLopDays(1L, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));

        assertThat(lopDays).isEqualByComparingTo("1.5");
    }

    @Test
    void computeLopDays_withNoRecords_returnsZero() {
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)))
                .thenReturn(List.of());

        BigDecimal lopDays = payrollComputationService.computeLopDays(1L, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));

        assertThat(lopDays).isEqualByComparingTo("0");
    }

    private DailyAttendance attendanceOn(LocalDate date, AttendanceStatus status) {
        Employee employee = employeeWith(null, EmployeeStatus.ACTIVE);
        return new DailyAttendance(employee, date, status);
    }

    // ---- computeBasicPay ----

    @Test
    void computeBasicPay_withNoLop_equalsFullPayScaleMinimum() {
        GradeScaleMaster gradeScale = gradeScale(ScaleType.IDA, "50000.00");

        BigDecimal basicPay = payrollComputationService.computeBasicPay(gradeScale,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25), BigDecimal.ZERO);

        assertThat(basicPay).isEqualByComparingTo("50000.00");
    }

    @Test
    void computeBasicPay_withLopDays_proratesByPayableOverTotalDays() {
        // 31-day cycle, 3.5 LOP days -> 27.5 payable / 31 total.
        GradeScaleMaster gradeScale = gradeScale(ScaleType.IDA, "31000.00");

        BigDecimal basicPay = payrollComputationService.computeBasicPay(gradeScale,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25), new BigDecimal("3.5"));

        // 31000 * 27.5 / 31 = 27500.00
        assertThat(basicPay).isEqualByComparingTo("27500.00");
    }

    // ---- resolveCurrentGradeScale / compute's dependency on it ----

    @Test
    void compute_whenNoCurrentPayFixation_throwsBusinessRuleViolationException() {
        Employee employee = employeeWith(null, EmployeeStatus.ACTIVE);
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.empty());
        PayrollRun run = new PayrollRun(2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));

        assertThatThrownBy(() -> payrollComputationService.compute(employee, run))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- resolveDaPercentage ----

    @Test
    void resolveDaPercentage_returnsMostRecentActiveRateOnOrBeforeDate() {
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 25)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));

        BigDecimal rate = payrollComputationService.resolveDaPercentage(ScaleType.IDA, LocalDate.of(2026, 8, 25));

        assertThat(rate).isEqualByComparingTo("17.00");
    }

    @Test
    void resolveDaPercentage_whenNoRateConfigured_throwsBusinessRuleViolationException() {
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 25)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollComputationService.resolveDaPercentage(ScaleType.IDA, LocalDate.of(2026, 8, 25)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- computeDearnessAllowance ----

    @Test
    void computeDearnessAllowance_appliesPercentageToBasic() {
        BigDecimal da = payrollComputationService.computeDearnessAllowance(new BigDecimal("50000.00"), new BigDecimal("17.00"));

        assertThat(da).isEqualByComparingTo("8500.00");
    }

    // ---- resolveCityClass ----

    @Test
    void resolveCityClass_withRegionalOfficeAssigned_returnsItsCityClass() {
        Employee employee = employeeWith(regionalOffice(CityClass.Y), EmployeeStatus.ACTIVE);

        assertThat(payrollComputationService.resolveCityClass(employee)).isEqualTo(CityClass.Y);
    }

    @Test
    void resolveCityClass_withoutRegionalOffice_defaultsToZ() {
        Employee employee = employeeWith(null, EmployeeStatus.ACTIVE);

        assertThat(payrollComputationService.resolveCityClass(employee)).isEqualTo(CityClass.Z);
    }

    // ---- computeHouseRentAllowance ----

    @Test
    void computeHouseRentAllowance_appliesCorrectPercentPerCityClass() {
        assertThat(payrollComputationService.computeHouseRentAllowance(new BigDecimal("50000.00"), new BigDecimal("8500.00"), CityClass.X))
                .isEqualByComparingTo("14040.00");
        assertThat(payrollComputationService.computeHouseRentAllowance(new BigDecimal("50000.00"), new BigDecimal("8500.00"), CityClass.Y))
                .isEqualByComparingTo("9360.00");
        assertThat(payrollComputationService.computeHouseRentAllowance(new BigDecimal("50000.00"), new BigDecimal("8500.00"), CityClass.Z))
                .isEqualByComparingTo("4680.00");
    }

    // ---- computeTransportAllowance ----

    @Test
    void computeTransportAllowance_appliesDaLinkedMultiplierToBase() {
        BigDecimal ta = payrollComputationService.computeTransportAllowance(new BigDecimal("17.00"));

        // 1600 * 1.17 = 1872.00
        assertThat(ta).isEqualByComparingTo("1872.00");
    }

    // ---- computeEpfEps ----

    @Test
    void computeEpfEps_belowEpsCeiling_epsUsesFullPfWages() {
        PayrollComputationService.EpfEpsResult result = payrollComputationService.computeEpfEps(
                new BigDecimal("8000.00"), new BigDecimal("2000.00")); // pfWages = 10000

        assertThat(result.employeeEpf()).isEqualByComparingTo("1200.00");
        assertThat(result.employerEps()).isEqualByComparingTo("833.00");
        assertThat(result.employerEpf()).isEqualByComparingTo("367.00");
        // Employer's total 12% must always equal the employee's 12% (same wage base, same rate).
        assertThat(result.employerEpf().add(result.employerEps())).isEqualByComparingTo(result.employeeEpf());
    }

    @Test
    void computeEpfEps_abovePfCeiling_epsIsCappedAt15000() {
        PayrollComputationService.EpfEpsResult result = payrollComputationService.computeEpfEps(
                new BigDecimal("50000.00"), new BigDecimal("8500.00")); // pfWages = 58500

        assertThat(result.employeeEpf()).isEqualByComparingTo("7020.00");
        assertThat(result.employerEps()).isEqualByComparingTo("1249.50"); // 15000 * 0.0833
        assertThat(result.employerEpf()).isEqualByComparingTo("5770.50");
        assertThat(result.employerEpf().add(result.employerEps())).isEqualByComparingTo(result.employeeEpf());
    }

    // ---- isSalaryHeld ----

    @Test
    void isSalaryHeld_forActiveEmployee_isFalse() {
        Employee employee = employeeWith(null, EmployeeStatus.ACTIVE);

        assertThat(payrollComputationService.isSalaryHeld(employee)).isFalse();
    }

    @Test
    void isSalaryHeld_forTerminatedEmployee_isTrue() {
        Employee employee = employeeWith(null, EmployeeStatus.TERMINATED);

        assertThat(payrollComputationService.isSalaryHeld(employee)).isTrue();
    }

    // ---- compute (end-to-end aggregation) ----

    @Test
    void compute_aggregatesAllFormulasCorrectly() {
        Employee employee = employeeWith(regionalOffice(CityClass.X), EmployeeStatus.ACTIVE);
        GradeScaleMaster gradeScale = gradeScale(ScaleType.IDA, "50000.00");
        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2020, 1, 15));

        PayrollRun run = new PayrollRun(2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));

        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)))
                .thenReturn(List.of());
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 25)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));

        PayrollComputationService.PayrollComputationResult result = payrollComputationService.compute(employee, run);

        assertThat(result.lopDays()).isEqualByComparingTo("0");
        assertThat(result.basicPay()).isEqualByComparingTo("50000.00");
        assertThat(result.dearnessAllowance()).isEqualByComparingTo("8500.00");
        assertThat(result.houseRentAllowance()).isEqualByComparingTo("14040.00");
        assertThat(result.transportAllowance()).isEqualByComparingTo("1872.00");
        assertThat(result.totalEarnings()).isEqualByComparingTo("74412.00");
        assertThat(result.employeeEpf()).isEqualByComparingTo("7020.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("7020.00");
        assertThat(result.employerContributions()).isEqualByComparingTo("7020.00");
        assertThat(result.netPay()).isEqualByComparingTo("67392.00");
        assertThat(result.hold()).isFalse();
    }

    @Test
    void compute_forTerminatedEmployee_stillComputesButFlagsHold() {
        Employee employee = employeeWith(regionalOffice(CityClass.X), EmployeeStatus.TERMINATED);
        GradeScaleMaster gradeScale = gradeScale(ScaleType.IDA, "50000.00");
        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2020, 1, 15));

        PayrollRun run = new PayrollRun(2026, 8, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25));

        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(1L,
                LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)))
                .thenReturn(List.of());
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 8, 25)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("17.00"), true)));

        PayrollComputationService.PayrollComputationResult result = payrollComputationService.compute(employee, run);

        assertThat(result.hold()).isTrue();
        assertThat(result.netPay()).isEqualByComparingTo("67392.00");
    }
}
