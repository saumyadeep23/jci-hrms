package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.MobilePunch;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.ReviewStatus;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.entity.TourRequestStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.MobilePunchRepository;
import in.gov.jci.hrms.repository.TourRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceAggregationServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // "Today" pinned to 2026-08-25 (Tuesday) IST.
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), IST);

    @Mock
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private MobilePunchRepository mobilePunchRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TourRequestRepository tourRequestRepository;
    @Mock
    private AttendanceLeaveDeductionService attendanceLeaveDeductionService;
    @Mock
    private ShiftResolutionService shiftResolutionService;

    private AttendanceAggregationService service;
    private Employee employee;
    /** JCI Circular JCI/HO/Pers/2024-25/53's HO/RO shift - every test in this file predates shift-based resolution and assumes exactly these numbers. */
    private ShiftMaster hoRoShift;

    @BeforeEach
    void setUp() {
        service = new AttendanceAggregationService(dailyAttendanceRepository, leaveApplicationRepository,
                holidayRepository, mobilePunchRepository, employeeRepository, tourRequestRepository,
                attendanceLeaveDeductionService, shiftResolutionService, FIXED_CLOCK);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        hoRoShift = new ShiftMaster("HO-RO", "HO/RO Shift", LocalTime.of(9, 45), LocalTime.of(18, 15), 30, false, true);
        hoRoShift.setFullDayMinutes(510);
        hoRoShift.setHalfDayMinutes(255);
    }

    /** Common stubs every evaluateDay() call needs regardless of which pipeline branch it exercises. */
    private void mockBaseline(LocalDate date) {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDate(EMPLOYEE_ID, date)).thenReturn(Optional.empty());
        when(dailyAttendanceRepository.saveAndFlush(any(DailyAttendance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, date, date)).thenReturn(List.of());
        mockShiftResolution(date);
    }

    /** Mirrors the pre-shift-resolution hardcoded HO/RO Mon-Fri / Sat-Sun weekly-off split, since every test employee here has no RO/DPC posting (defaults to the HO/RO 5-day week - see ShiftResolutionService). */
    private void mockShiftResolution(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean weeklyOff = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        var details = weeklyOff ? ShiftResolutionService.ShiftDetails.weeklyOff(hoRoShift) : ShiftResolutionService.ShiftDetails.workingDay(hoRoShift);
        when(shiftResolutionService.resolveShiftForEmployee(EMPLOYEE_ID, date)).thenReturn(details);
    }

    private void mockNoPunches() {
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    private void mockPunches(String inUtc, String outUtc) {
        List<MobilePunch> punches = outUtc == null
                ? List.of(punchAt(inUtc, PunchType.IN))
                : List.of(punchAt(inUtc, PunchType.IN), punchAt(outUtc, PunchType.OUT));
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(any(), any(), any()))
                .thenReturn(punches);
    }

    private MobilePunch punchAt(String utcInstant, PunchType type) {
        return new MobilePunch(employee, Instant.parse(utcInstant), type,
                new BigDecimal("28.6139"), new BigDecimal("77.2090"), true, ReviewStatus.VALID);
    }

    // ---- Step 3: punch evaluation ----

    @Test
    void evaluateDay_onTimeInAndOut_isPresent() {
        LocalDate date = LocalDate.of(2026, 8, 20); // Thursday
        mockBaseline(date);
        mockPunches("2026-08-20T04:00:00Z", "2026-08-20T12:50:00Z"); // 09:30-18:20 IST, 8h50m

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.PRESENT);
        assertThat(response.inTime()).isEqualTo("09:30:00");
        assertThat(response.outTime()).isEqualTo("18:20:00");
    }

    @Test
    void evaluateDay_inFlexGraceWindowWithFullHours_isGraceApplied() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:30:00Z", "2026-08-20T13:05:00Z"); // 10:00-18:35 IST, 8h35m

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.GRACE_APPLIED);
        assertThat(response.remarks()).contains("Grace applied");
    }

    @Test
    void evaluateDay_inFlexGraceWindowWithShortHours_isLateShortHours() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:30:00Z", "2026-08-20T11:30:00Z"); // 10:00-17:00 IST, 7h

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.LATE_SHORT_HOURS);
    }

    @Test
    void evaluateDay_lateArrivalConcessionWindow_firstOccurrence_requiresRegularization() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        mockBaseline(date);
        mockPunches("2026-08-24T05:00:00Z", "2026-08-24T12:50:00Z"); // 10:30-18:20 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                any(), any(), any(), any())).thenReturn(0L);

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.REQUIRES_REGULARIZATION);
    }

    @Test
    void evaluateDay_lateArrivalConcessionWindow_exceedsMonthlyLimit_isUnauthorizedLate() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        mockBaseline(date);
        mockPunches("2026-08-24T05:00:00Z", "2026-08-24T12:50:00Z"); // 10:30-18:20 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                any(), any(), any(), any())).thenReturn(3L);

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.UNAUTHORIZED_LATE);
    }

    @Test
    void evaluateDay_earlyDepartureConcessionWindow_isRequiresRegularization() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        mockBaseline(date);
        mockPunches("2026-08-24T04:00:00Z", "2026-08-24T12:15:00Z"); // 09:30-17:45 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                any(), any(), any(), any())).thenReturn(0L);

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.REQUIRES_REGULARIZATION);
    }

    @Test
    void evaluateDay_severelyLateWithLowHours_isAbsent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T05:30:00Z", "2026-08-20T06:30:00Z"); // 11:00-12:00 IST, 1h

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ABSENT);
    }

    @Test
    void evaluateDay_severelyLateWithHalfDayHours_isHalfDayAbsent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T05:30:00Z", "2026-08-20T10:30:00Z"); // 11:00-16:00 IST, 5h

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HALF_DAY_ABSENT);
    }

    // ---- HO/RO 510/255-minute full/half-day boundary (JCI Circular JCI/HO/Pers/2024-25/53, 09:45-18:15) ----

    @Test
    void evaluateDay_workedExactlyFullDayMinutes_isPresent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:15:00Z", "2026-08-20T12:45:00Z"); // 09:45-18:15 IST exactly, 510 min

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.PRESENT);
    }

    @Test
    void evaluateDay_worked480Minutes_isHalfDayAbsent_sinceBelowThe510FullDayThreshold() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T05:30:00Z", "2026-08-20T13:30:00Z"); // 11:00-19:00 IST, 480 min

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HALF_DAY_ABSENT);
    }

    @Test
    void evaluateDay_workedExactlyHalfDayMinutes_isHalfDayAbsent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T05:30:00Z", "2026-08-20T09:45:00Z"); // 11:00-15:15 IST, 255 min

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HALF_DAY_ABSENT);
    }

    @Test
    void evaluateDay_workedOneMinuteBelowHalfDayMinutes_isAbsent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T05:30:00Z", "2026-08-20T09:44:00Z"); // 11:00-15:14 IST, 254 min

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ABSENT);
    }

    @Test
    void evaluateDay_noPunchesOnWeekday_isAbsent() {
        LocalDate date = LocalDate.of(2026, 8, 20); // Thursday
        mockBaseline(date);
        mockNoPunches();
        when(holidayRepository.findByHolidayDate(date)).thenReturn(List.of());

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ABSENT);
    }

    // ---- Step 2: holiday / weekly-off ----

    @Test
    void evaluateDay_weekendWithNoPunches_isWeekoff() {
        LocalDate date = LocalDate.of(2026, 8, 22); // Saturday
        mockBaseline(date);
        mockNoPunches();
        when(holidayRepository.findByHolidayDate(date)).thenReturn(List.of());

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.WEEKOFF);
    }

    @Test
    void evaluateDay_gazettedHolidayWithNoPunches_isHoliday() {
        LocalDate date = LocalDate.of(2026, 8, 20); // Thursday, made a holiday for this test
        mockBaseline(date);
        mockNoPunches();
        when(holidayRepository.findByHolidayDate(date))
                .thenReturn(List.of(new Holiday(date, "Special Holiday", HolidayType.GAZETTED)));

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HOLIDAY);
        assertThat(response.remarks()).isEqualTo("Special Holiday");
    }

    @Test
    void evaluateDay_weekendWithPunches_fallsThroughToStepThree() {
        LocalDate date = LocalDate.of(2026, 8, 22); // Saturday, but employee worked
        mockBaseline(date);
        mockPunches("2026-08-22T04:00:00Z", "2026-08-22T12:50:00Z"); // 09:30-18:20 IST

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.PRESENT);
    }

    // ---- Step 1: sanctioned leave ----

    private LeaveType leaveType(String code) {
        LeaveType leaveType = new LeaveType(code, code + " Leave", BigDecimal.TEN, false, true);
        ReflectionTestUtils.setField(leaveType, "id", 5L);
        return leaveType;
    }

    @Test
    void evaluateDay_fullDaySanctionedLeave_isOnLeave() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        LeaveType cl = leaveType("CL");
        LeaveApplication application = new LeaveApplication(employee, cl, date, date, BigDecimal.ONE, "Personal work");
        application.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, date, date)).thenReturn(List.of(application));

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ON_LEAVE);
        assertThat(response.remarks()).isEqualTo("Sanctioned CL Leave");
        assertThat(response.leaveTypeCode()).isEqualTo("CL");
    }

    @Test
    void evaluateDay_halfDayLeaveWithRequiredPunchSessionMet_isHalfDayPresent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        LeaveType cl = leaveType("CL");
        LeaveApplication application = new LeaveApplication(employee, cl, date, date,
                new BigDecimal("0.5"), "Half day");
        application.setStatus(LeaveApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(application, "leaveSession", LeaveSession.SECOND_HALF);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, date, date)).thenReturn(List.of(application));
        // 2nd Half requires a punch 09:45-10:15 IST; worked till end of day comfortably clears 255 min.
        mockPunches("2026-08-20T04:15:00Z", "2026-08-20T12:30:00Z"); // 09:45-18:00 IST

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HALF_DAY_PRESENT);
        assertThat(response.remarks()).isEqualTo("Half Day Leave (0.5 CL)");
    }

    @Test
    void evaluateDay_halfDayLeaveWithRequiredPunchSessionMissed_isHalfDayShort() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        LeaveType cl = leaveType("CL");
        LeaveApplication application = new LeaveApplication(employee, cl, date, date,
                new BigDecimal("0.5"), "Half day");
        application.setStatus(LeaveApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(application, "leaveSession", LeaveSession.SECOND_HALF);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, date, date)).thenReturn(List.of(application));
        // Punched in well outside the required 09:45-10:15 window.
        mockPunches("2026-08-20T06:00:00Z", "2026-08-20T12:30:00Z"); // 11:30-18:00 IST

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.HALF_DAY_SHORT);
    }

    // ---- Step 1b: tour / official duty ----

    private TourRequest approvedTour(LocalDate start, LocalDate end, String destination) {
        TourRequest tour = new TourRequest("TR-001", employee, "Field inspection", "Kolkata", destination,
                start, end, false);
        tour.setStatus(TourRequestStatus.APPROVED);
        return tour;
    }

    @Test
    void evaluateDay_approvedTourWithoutPunches_isOnTourPresent() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockNoPunches();
        when(tourRequestRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, TourRequestStatus.APPROVED, date, date))
                .thenReturn(List.of(approvedTour(date.minusDays(1), date.plusDays(2), "Bhubaneswar")));

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ON_TOUR);
        assertThat(response.remarks()).isEqualTo("Official Tour / OD - Sanctioned to Bhubaneswar");
        assertThat(response.tourRequestNumber()).isEqualTo("TR-001");
        assertThat(response.tourDestination()).isEqualTo("Bhubaneswar");
    }

    @Test
    void evaluateDay_approvedTourWithPunches_isOnTourPresentWithPunchedNote() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:00:00Z", "2026-08-20T12:50:00Z");
        when(tourRequestRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, TourRequestStatus.APPROVED, date, date))
                .thenReturn(List.of(approvedTour(date, date, "Bhubaneswar")));

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ON_TOUR);
        assertThat(response.remarks()).isEqualTo("Official Tour / OD - Sanctioned to Bhubaneswar (Tour + Punched)");
    }

    @Test
    void evaluateDay_approvedTourTakesPrecedenceOverWeekendCalendar() {
        LocalDate date = LocalDate.of(2026, 8, 22); // Saturday
        mockBaseline(date);
        mockNoPunches();
        when(tourRequestRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, TourRequestStatus.APPROVED, date, date))
                .thenReturn(List.of(approvedTour(date, date, "Bhubaneswar")));

        var response = service.evaluateDay(EMPLOYEE_ID, date);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ON_TOUR);
    }

    // ---- today-still-open / future-date handling ----

    @Test
    void evaluateDay_todayWithInPunchButNoOutYet_isInProgressAndPersisted() {
        LocalDate today = LocalDate.of(2026, 8, 25);
        mockBaseline(today);
        mockPunches("2026-08-25T04:00:00Z", null); // 09:30 IST, only an IN punch so far

        var response = service.evaluateDay(EMPLOYEE_ID, today);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.IN_PROGRESS);
        assertThat(response.inTime()).isEqualTo("09:30:00");
        assertThat(response.outTime()).isNull();
        verify(dailyAttendanceRepository).saveAndFlush(any(DailyAttendance.class));
    }

    @Test
    void evaluateDay_todayWithNoPunchesAtAll_throwsBusinessRuleViolationException() {
        // Deliberately not using mockBaseline() - the pipeline returns null before
        // ever reaching dailyAttendanceRepository.findByEmployeeIdAndAttendanceDate/
        // saveAndFlush, so stubbing those would be flagged as unnecessary under
        // Mockito's strict stubs.
        LocalDate today = LocalDate.of(2026, 8, 25);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, today, today)).thenReturn(List.of());
        mockShiftResolution(today);
        mockNoPunches();

        assertThatThrownBy(() -> service.evaluateDay(EMPLOYEE_ID, today))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void evaluateDay_futureDate_throwsBusinessRuleViolationException() {
        LocalDate future = LocalDate.of(2026, 8, 26);

        assertThatThrownBy(() -> service.evaluateDay(EMPLOYEE_ID, future))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void evaluateDay_todayFullDayOnLeave_isResolvedDespiteNoPunches() {
        LocalDate today = LocalDate.of(2026, 8, 25);
        mockBaseline(today);
        LeaveType el = leaveType("EL");
        LeaveApplication application = new LeaveApplication(employee, el, today, today, BigDecimal.ONE, "Vacation");
        application.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, today, today)).thenReturn(List.of(application));

        var response = service.evaluateDay(EMPLOYEE_ID, today);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.ON_LEAVE);
    }

    // ---- evaluateMonth boundaries ----

    @Test
    void evaluateMonth_futureMonth_returnsEmptyList() {
        assertThat(service.evaluateMonth(EMPLOYEE_ID, 2026, 9)).isEmpty();
    }

    @Test
    void evaluateMonth_invalidMonth_throwsBusinessRuleViolationException() {
        assertThatThrownBy(() -> service.evaluateMonth(EMPLOYEE_ID, 2026, 13))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- auto-debit trigger wiring (Phase C) ----

    @Test
    void evaluateDay_unauthorizedLate_triggersLeaveDeduction() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        mockBaseline(date);
        mockPunches("2026-08-24T05:00:00Z", "2026-08-24T12:50:00Z"); // 10:30-18:20 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                any(), any(), any(), any())).thenReturn(3L);

        service.evaluateDay(EMPLOYEE_ID, date);

        org.mockito.Mockito.verify(attendanceLeaveDeductionService)
                .debitForUnauthorizedAttendance(any(Employee.class), any(DailyAttendance.class));
    }

    @Test
    void evaluateDay_lateShortHours_triggersLeaveDeduction() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:30:00Z", "2026-08-20T11:30:00Z"); // 10:00-17:00 IST, 7h

        service.evaluateDay(EMPLOYEE_ID, date);

        org.mockito.Mockito.verify(attendanceLeaveDeductionService)
                .debitForUnauthorizedAttendance(any(Employee.class), any(DailyAttendance.class));
    }

    @Test
    void evaluateDay_requiresRegularization_doesNotTriggerLeaveDeduction() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        mockBaseline(date);
        mockPunches("2026-08-24T05:00:00Z", "2026-08-24T12:50:00Z"); // 10:30-18:20 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                any(), any(), any(), any())).thenReturn(0L);

        service.evaluateDay(EMPLOYEE_ID, date);

        org.mockito.Mockito.verifyNoInteractions(attendanceLeaveDeductionService);
    }

    @Test
    void evaluateDay_present_doesNotTriggerLeaveDeduction() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        mockBaseline(date);
        mockPunches("2026-08-20T04:00:00Z", "2026-08-20T12:50:00Z"); // 09:30-18:20 IST

        service.evaluateDay(EMPLOYEE_ID, date);

        org.mockito.Mockito.verifyNoInteractions(attendanceLeaveDeductionService);
    }

    // ---- DPC 6-day work-week (ShiftResolutionService integration) ----

    private static final Long DPC_EMPLOYEE_ID = 2L;

    private Employee dpcEmployee() {
        Department department = new Department("OPS", "Operations");
        Designation designation = new Designation("DPC Assistant");
        Employee employee = new Employee("EMP-002", "Bina", "Das", "bina.das@example.com",
                LocalDate.of(2023, 6, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", DPC_EMPLOYEE_ID);
        return employee;
    }

    private ShiftMaster dpcShift(String code, LocalTime start, LocalTime end, int fullDayMinutes, int halfDayMinutes) {
        ShiftMaster shift = new ShiftMaster(code, code, start, end, 15, false, true);
        shift.setFullDayMinutes(fullDayMinutes);
        shift.setHalfDayMinutes(halfDayMinutes);
        return shift;
    }

    /** Mirrors mockBaseline() but for the DPC employee/shift, so these tests don't disturb the HO/RO-employee tests above. */
    private void mockDpcBaseline(LocalDate date, Employee employee, ShiftResolutionService.ShiftDetails shiftDetails) {
        when(employeeRepository.findById(DPC_EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDate(DPC_EMPLOYEE_ID, date)).thenReturn(Optional.empty());
        when(dailyAttendanceRepository.saveAndFlush(any(DailyAttendance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                DPC_EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, date, date)).thenReturn(List.of());
        when(shiftResolutionService.resolveShiftForEmployee(DPC_EMPLOYEE_ID, date)).thenReturn(shiftDetails);
    }

    @Test
    void evaluateDay_dpcEmployee_saturdayCompliantPunches_isPresentNotWeekoff() {
        LocalDate saturday = LocalDate.of(2026, 8, 22);
        Employee dpcEmployee = dpcEmployee();
        ShiftMaster dpcSat = dpcShift("DPC_SAT", LocalTime.of(10, 0), LocalTime.of(14, 30), 270, 135);
        mockDpcBaseline(saturday, dpcEmployee, ShiftResolutionService.ShiftDetails.workingDay(dpcSat));
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(eq(DPC_EMPLOYEE_ID), any(), any()))
                .thenReturn(List.of(
                        new MobilePunch(dpcEmployee, Instant.parse("2026-08-22T04:30:00Z"), PunchType.IN,
                                new BigDecimal("22.5726"), new BigDecimal("88.3639"), true, ReviewStatus.VALID), // 10:00 IST
                        new MobilePunch(dpcEmployee, Instant.parse("2026-08-22T09:00:00Z"), PunchType.OUT,
                                new BigDecimal("22.5726"), new BigDecimal("88.3639"), true, ReviewStatus.VALID))); // 14:30 IST

        var response = service.evaluateDay(DPC_EMPLOYEE_ID, saturday);

        // A DPC employee's Saturday is a working day (DPC_SAT), not weekly off - the exact
        // behavior JCI's 6-day DPC work-week needs and a 5-day HO/RO employee's Saturday doesn't get.
        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.PRESENT);
    }

    @Test
    void evaluateDay_dpcEmployee_sundayNoPunches_isWeekoff() {
        LocalDate sunday = LocalDate.of(2026, 8, 23);
        Employee dpcEmployee = dpcEmployee();
        ShiftMaster dpcWeekday = dpcShift("DPC_WD", LocalTime.of(10, 0), LocalTime.of(17, 30), 450, 225);
        mockDpcBaseline(sunday, dpcEmployee, ShiftResolutionService.ShiftDetails.weeklyOff(dpcWeekday));
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(eq(DPC_EMPLOYEE_ID), any(), any()))
                .thenReturn(List.of());
        when(holidayRepository.findByHolidayDate(sunday)).thenReturn(List.of());

        var response = service.evaluateDay(DPC_EMPLOYEE_ID, sunday);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.WEEKOFF);
    }

    @Test
    void evaluateDay_dpcEmployee_weekdayArrivalPastFifteenMinuteGrace_requiresRegularization() {
        LocalDate thursday = LocalDate.of(2026, 8, 20);
        Employee dpcEmployee = dpcEmployee();
        ShiftMaster dpcWeekday = dpcShift("DPC_WD", LocalTime.of(10, 0), LocalTime.of(17, 30), 450, 225);
        mockDpcBaseline(thursday, dpcEmployee, ShiftResolutionService.ShiftDetails.workingDay(dpcWeekday));
        // 10:20 IST arrival - 5 minutes past DPC's 15-min grace cutoff (10:15), but within the
        // late-arrival concession window (up to 10:30 = start + 2*grace), unlike HO/RO's 30-min grace.
        when(mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(eq(DPC_EMPLOYEE_ID), any(), any()))
                .thenReturn(List.of(
                        new MobilePunch(dpcEmployee, Instant.parse("2026-08-20T04:50:00Z"), PunchType.IN,
                                new BigDecimal("22.5726"), new BigDecimal("88.3639"), true, ReviewStatus.VALID),
                        new MobilePunch(dpcEmployee, Instant.parse("2026-08-20T12:00:00Z"), PunchType.OUT,
                                new BigDecimal("22.5726"), new BigDecimal("88.3639"), true, ReviewStatus.VALID))); // 17:30 IST
        when(dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                eq(DPC_EMPLOYEE_ID), any(), any(), any())).thenReturn(0L);

        var response = service.evaluateDay(DPC_EMPLOYEE_ID, thursday);

        assertThat(response.detailStatus()).isEqualTo(AttendanceDetailStatus.REQUIRES_REGULARIZATION);
    }
}
