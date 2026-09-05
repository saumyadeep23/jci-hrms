package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DailyAttendanceSummaryResponse;
import in.gov.jci.hrms.dto.MobilePunchRequest;
import in.gov.jci.hrms.dto.MobilePunchResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.MobilePunch;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.ReviewStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.MobilePunchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobilePunchServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private MobilePunchRepository mobilePunchRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private GeofenceService geofenceService;
    @Mock
    private AttendanceAggregationService attendanceAggregationService;

    private MobilePunchService mobilePunchService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        mobilePunchService = new MobilePunchService(mobilePunchRepository, employeeRepository, geofenceService,
                attendanceAggregationService);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private MobilePunchRequest validRequest() {
        return new MobilePunchRequest(EMPLOYEE_ID, Instant.parse("2026-08-23T09:00:00Z"), PunchType.IN,
                new BigDecimal("28.6139"), new BigDecimal("77.2090"), new BigDecimal("5.0"), "device-123", null);
    }

    @Test
    void create_whenWithinGeofence_savesAsValid() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(geofenceService.isWithinGeofence(any(Employee.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(true);
        when(mobilePunchRepository.saveAndFlush(any(MobilePunch.class))).thenAnswer(inv -> {
            MobilePunch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        MobilePunchResponse response = mobilePunchService.create(validRequest());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.isWithinGeofence()).isTrue();
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.VALID);
    }

    @Test
    void create_whenOutsideGeofence_flagsForReview() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(geofenceService.isWithinGeofence(any(Employee.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(false);

        ArgumentCaptor<MobilePunch> captor = ArgumentCaptor.forClass(MobilePunch.class);
        when(mobilePunchRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> {
            MobilePunch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        MobilePunchResponse response = mobilePunchService.create(validRequest());

        assertThat(response.isWithinGeofence()).isFalse();
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.FLAGGED_FOR_REVIEW);
        assertThat(captor.getValue().getReviewStatus()).isEqualTo(ReviewStatus.FLAGGED_FOR_REVIEW);
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mobilePunchService.create(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void create_neverRejectsThePunchItself_alwaysSavesRegardlessOfGeofenceResult() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(geofenceService.isWithinGeofence(any(Employee.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(false);
        when(mobilePunchRepository.saveAndFlush(any(MobilePunch.class))).thenAnswer(inv -> {
            MobilePunch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 102L);
            return saved;
        });

        MobilePunchResponse response = mobilePunchService.create(validRequest());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(102L);
    }

    @Test
    void create_syncsDailyAttendanceForThePunchsIstDate() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(geofenceService.isWithinGeofence(any(Employee.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(true);
        when(mobilePunchRepository.saveAndFlush(any(MobilePunch.class))).thenAnswer(inv -> inv.getArgument(0));

        mobilePunchService.create(validRequest()); // punchTime 2026-08-23T09:00:00Z = 2026-08-23T14:30 IST

        verify(attendanceAggregationService).evaluateDay(EMPLOYEE_ID, LocalDate.of(2026, 8, 23));
    }

    @Test
    void create_whenDailyAttendanceSyncThrows_thePunchStillSucceeds() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(geofenceService.isWithinGeofence(any(Employee.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(true);
        when(mobilePunchRepository.saveAndFlush(any(MobilePunch.class))).thenAnswer(inv -> {
            MobilePunch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 103L);
            return saved;
        });
        when(attendanceAggregationService.evaluateDay(any(), any()))
                .thenThrow(new BusinessRuleViolationException("boom"));

        MobilePunchResponse response = mobilePunchService.create(validRequest());

        assertThat(response.id()).isEqualTo(103L);
    }

    // ---- getMyHistory ----

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // "Now" pinned to 2026-08-24T23:30 IST, so LocalDate.now(clock) in IST is 2026-08-24 -
    // getMyHistory's day-enumeration for year=2026/month=8 then runs Aug 1..Aug 24 inclusive.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-24T18:00:00Z"), IST);

    private MobilePunchService historyService;

    private MobilePunch punchAt(String utcInstant, PunchType type) {
        return new MobilePunch(employee, Instant.parse(utcInstant), type,
                new BigDecimal("28.6139"), new BigDecimal("77.2090"), true, ReviewStatus.VALID);
    }

    /** Finds the row for a specific date rather than assuming index 0 - the response now has one row per calendar day, not just days with punches. */
    private DailyAttendanceSummaryResponse dayEntry(List<DailyAttendanceSummaryResponse> history, LocalDate date) {
        return history.stream().filter(d -> d.date().equals(date)).findFirst()
                .orElseThrow(() -> new AssertionError("No entry for " + date));
    }

    @BeforeEach
    void setUpHistoryService() {
        historyService = new MobilePunchService(mobilePunchRepository, employeeRepository, geofenceService,
                attendanceAggregationService, FIXED_CLOCK);
    }

    @Test
    void getMyHistory_computesServiceHoursAndPresentStatus_forFullDay() {
        // 09:30 IST -> 04:00Z, 18:00 IST -> 12:30Z (8h30m worked)
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of(
                        punchAt("2026-08-24T04:00:00Z", PunchType.IN),
                        punchAt("2026-08-24T12:30:00Z", PunchType.OUT)));

        List<DailyAttendanceSummaryResponse> history = historyService.getMyHistory(EMPLOYEE_ID, 2026, 8);

        // Aug 1 through Aug 24 inclusive (today, per FIXED_CLOCK).
        assertThat(history).hasSize(24);
        DailyAttendanceSummaryResponse day = dayEntry(history, LocalDate.of(2026, 8, 24));
        assertThat(day.inTime()).isEqualTo("09:30:00");
        assertThat(day.outTime()).isEqualTo("18:00:00");
        assertThat(day.serviceHours()).isEqualTo("8.50 Hrs (8h 30m)");
        assertThat(day.status()).isEqualTo("PRESENT");
    }

    @Test
    void getMyHistory_dayWithNoPunches_isAbsent() {
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of(punchAt("2026-08-24T04:00:00Z", PunchType.IN)));

        DailyAttendanceSummaryResponse day = dayEntry(
                historyService.getMyHistory(EMPLOYEE_ID, 2026, 8), LocalDate.of(2026, 8, 10));

        assertThat(day.inTime()).isNull();
        assertThat(day.outTime()).isNull();
        assertThat(day.serviceHours()).isNull();
        assertThat(day.status()).isEqualTo("ABSENT");
    }

    @Test
    void getMyHistory_withNoOutPunchYet_isInProgress() {
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of(punchAt("2026-08-24T04:00:00Z", PunchType.IN)));

        DailyAttendanceSummaryResponse day = dayEntry(
                historyService.getMyHistory(EMPLOYEE_ID, 2026, 8), LocalDate.of(2026, 8, 24));

        assertThat(day.inTime()).isEqualTo("09:30:00");
        assertThat(day.outTime()).isNull();
        assertThat(day.serviceHours()).isEqualTo("In Progress");
        assertThat(day.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getMyHistory_withLunchBreakPunches_usesFirstInAndLastOut() {
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of(
                        punchAt("2026-08-24T03:30:00Z", PunchType.IN),  // 09:00 IST
                        punchAt("2026-08-24T07:30:00Z", PunchType.OUT), // 13:00 IST - lunch out
                        punchAt("2026-08-24T08:30:00Z", PunchType.IN),  // 14:00 IST - lunch back in
                        punchAt("2026-08-24T12:30:00Z", PunchType.OUT))); // 18:00 IST

        DailyAttendanceSummaryResponse day = dayEntry(
                historyService.getMyHistory(EMPLOYEE_ID, 2026, 8), LocalDate.of(2026, 8, 24));

        assertThat(day.inTime()).isEqualTo("09:00:00");
        assertThat(day.outTime()).isEqualTo("18:00:00");
        assertThat(day.serviceHours()).isEqualTo("9.00 Hrs (9h 0m)");
        assertThat(day.status()).isEqualTo("PRESENT");
    }

    @Test
    void getMyHistory_groupsByIstLocalDate_notUtcDate() {
        // 2026-08-23T19:00:00Z is 2026-08-24T00:30 IST - past UTC midnight but not yet IST midnight's mirror.
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of(punchAt("2026-08-23T19:00:00Z", PunchType.IN)));

        DailyAttendanceSummaryResponse day = dayEntry(
                historyService.getMyHistory(EMPLOYEE_ID, 2026, 8), LocalDate.of(2026, 8, 24));

        assertThat(day.inTime()).isEqualTo("00:30:00");
    }

    @Test
    void getMyHistory_currentMonth_stopsAtToday_doesNotIncludeFutureDays() {
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of());

        List<DailyAttendanceSummaryResponse> history = historyService.getMyHistory(EMPLOYEE_ID, 2026, 8);

        assertThat(history).extracting(DailyAttendanceSummaryResponse::date).doesNotContain(LocalDate.of(2026, 8, 25));
        assertThat(history).hasSize(24);
    }

    @Test
    void getMyHistory_futureMonth_returnsEmptyList() {
        List<DailyAttendanceSummaryResponse> history = historyService.getMyHistory(EMPLOYEE_ID, 2026, 9);

        assertThat(history).isEmpty();
    }

    @Test
    void getMyHistory_withInvalidMonth_throwsBusinessRuleViolationException() {
        assertThatThrownBy(() -> historyService.getMyHistory(EMPLOYEE_ID, 2026, 13))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
