package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DailyAttendanceDetailResponse;
import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.MobilePunch;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.entity.TourRequestStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.MobilePunchRepository;
import in.gov.jci.hrms.repository.TourRequestRepository;
import in.gov.jci.hrms.service.ShiftResolutionService.ShiftDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates one employee-day at a time against JCI Circular JCI/HO/Pers/2024-25/53
 * (office timing/grace/concession rules) and sanctioned leave/holiday data,
 * persisting the result to daily_attendance. Unlike MobilePunchService's
 * getMyHistory (which stays exactly as-is - a non-persisted, purely
 * descriptive on-the-fly view), this is the authoritative engine: it writes
 * daily_attendance rows for the first time in this codebase's history.
 *
 * Payroll (PayrollComputationService/PayrollReportingService) keeps reading
 * only the coarse AttendanceStatus column - see DailyAttendance.toCoarseStatus()
 * for the deterministic mapping every detail status collapses to, so this
 * service introduces no payroll behavior change on its own. LATE_SHORT_HOURS
 * and UNAUTHORIZED_LATE days instead trigger AttendanceLeaveDeductionService
 * inline (see evaluateAndPersist) - REQUIRES_REGULARIZATION does not, since
 * it's still within the monthly concession allowance and pending, not yet a
 * confirmed violation.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceAggregationService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * "Max 1 hr late concession, limit 2/month" - literal spec pseudocode is
     * "occurrences <= 2 -> REQUIRES_REGULARIZATION", so a 3rd occurrence
     * this month is still eligible; only the 4th+ becomes UNAUTHORIZED_LATE.
     */
    private static final int MONTHLY_CONCESSION_LIMIT = 2;
    private static final LocalTime FIRST_HALF_PUNCH_WINDOW_START = LocalTime.of(14, 0);
    private static final LocalTime FIRST_HALF_PUNCH_WINDOW_END = LocalTime.of(14, 30);

    private static final Set<AttendanceDetailStatus> CONCESSION_STATUSES =
            Set.of(AttendanceDetailStatus.REQUIRES_REGULARIZATION, AttendanceDetailStatus.UNAUTHORIZED_LATE);
    /** Days whose consequence is a leave-ledger debit, not extra loss-of-pay - see AttendanceLeaveDeductionService. */
    private static final Set<AttendanceDetailStatus> AUTO_DEBIT_TRIGGER_STATUSES =
            Set.of(AttendanceDetailStatus.LATE_SHORT_HOURS, AttendanceDetailStatus.UNAUTHORIZED_LATE);
    /** JCI's Head Office is in Kolkata - mirrors HolidayService.HO_STATE. */
    private static final String HO_STATE = "West Bengal";
    /** Holiday.state values meaning "applies everywhere" - mirrors HolidayService.isApplicable, plus "ALL" (the Holiday & RH Publisher's literal national marker, translated to Holiday.state == null at persistence time, but tolerated here too in case of legacy data). */
    private static final Set<String> NATIONAL_STATE_MARKERS = Set.of("CENTRAL", "ALL");

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final HolidayRepository holidayRepository;
    private final MobilePunchRepository mobilePunchRepository;
    private final EmployeeRepository employeeRepository;
    private final TourRequestRepository tourRequestRepository;
    private final AttendanceLeaveDeductionService attendanceLeaveDeductionService;
    private final ShiftResolutionService shiftResolutionService;
    private final Clock clock;

    @Autowired
    public AttendanceAggregationService(DailyAttendanceRepository dailyAttendanceRepository,
                                         LeaveApplicationRepository leaveApplicationRepository,
                                         HolidayRepository holidayRepository,
                                         MobilePunchRepository mobilePunchRepository,
                                         EmployeeRepository employeeRepository,
                                         TourRequestRepository tourRequestRepository,
                                         AttendanceLeaveDeductionService attendanceLeaveDeductionService,
                                         ShiftResolutionService shiftResolutionService) {
        this(dailyAttendanceRepository, leaveApplicationRepository, holidayRepository, mobilePunchRepository,
                employeeRepository, tourRequestRepository, attendanceLeaveDeductionService, shiftResolutionService,
                Clock.system(DISPLAY_ZONE));
    }

    /** Package-visible so tests can pin "today" - the today-is-still-open check is otherwise genuinely wall-clock-dependent. */
    AttendanceAggregationService(DailyAttendanceRepository dailyAttendanceRepository,
                                  LeaveApplicationRepository leaveApplicationRepository,
                                  HolidayRepository holidayRepository,
                                  MobilePunchRepository mobilePunchRepository,
                                  EmployeeRepository employeeRepository,
                                  TourRequestRepository tourRequestRepository,
                                  AttendanceLeaveDeductionService attendanceLeaveDeductionService,
                                  ShiftResolutionService shiftResolutionService,
                                  Clock clock) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.holidayRepository = holidayRepository;
        this.mobilePunchRepository = mobilePunchRepository;
        this.employeeRepository = employeeRepository;
        this.tourRequestRepository = tourRequestRepository;
        this.attendanceLeaveDeductionService = attendanceLeaveDeductionService;
        this.shiftResolutionService = shiftResolutionService;
        this.clock = clock;
    }

    /**
     * Evaluates and persists every day of the month from day 1 through
     * today (a future month/day hasn't happened yet, so it's simply not
     * included). A "today" with an IN punch but no OUT yet is persisted as
     * IN_PROGRESS (see evaluatePipelineOrNull) rather than skipped; only a
     * today with zero punches at all is skipped, since it's not yet
     * possible to say the day is ABSENT while it's still ongoing.
     *
     * The whole month is one transaction: if AttendanceLeaveDeductionService
     * hits a genuine configuration error (e.g. CL/EL/LWP leave types not
     * seeded), the entire month's evaluation rolls back rather than leaving
     * some days persisted and others not - a deliberate all-or-nothing unit
     * of work, not a bug.
     */
    @Transactional
    public List<DailyAttendanceDetailResponse> evaluateMonth(Long employeeId, int year, int month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new BusinessRuleViolationException("Invalid year/month: " + year + "-" + month);
        }

        LocalDate today = LocalDate.now(clock);
        YearMonth currentYearMonth = YearMonth.from(today);
        if (yearMonth.isAfter(currentYearMonth)) {
            return List.of();
        }
        LocalDate lastDayToEvaluate = yearMonth.equals(currentYearMonth) ? today : yearMonth.atEndOfMonth();

        Employee employee = resolveEmployee(employeeId);
        List<DailyAttendanceDetailResponse> results = new ArrayList<>();
        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(lastDayToEvaluate); date = date.plusDays(1)) {
            evaluateAndPersist(employee, date, today).ifPresent(results::add);
        }
        return results;
    }

    /**
     * Evaluates and persists a single day - e.g. to re-run after HR corrects
     * a punch or sanctions a leave application retroactively, or immediately
     * after a punch is saved (see MobilePunchService.create). Throws if the
     * date is in the future, or if it's today with zero punches at all yet
     * (a day with no attendance signal at all can't be finalized into
     * PRESENT/ABSENT/etc. while it's still ongoing) - but succeeds, returning
     * an IN_PROGRESS result, for today with an IN punch and no OUT yet.
     *
     * REQUIRES_NEW rather than the default REQUIRED: MobilePunchService.
     * syncDailyAttendance() calls this from inside its own punch-creation
     * transaction and deliberately catches any RuntimeException from it so a
     * sync failure can never fail the punch itself - but catching the
     * exception in Java doesn't undo Spring marking a shared (REQUIRED)
     * transaction rollback-only the moment it propagates through this
     * method's transactional boundary, which surfaced as an
     * UnexpectedRollbackException on the punch commit regardless of that
     * catch block. Running in its own transaction means a failure here
     * rolls back only this method's own work, leaving the punch's
     * transaction free to commit as intended.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyAttendanceDetailResponse evaluateDay(Long employeeId, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new BusinessRuleViolationException("Cannot evaluate a future date: " + date);
        }
        Employee employee = resolveEmployee(employeeId);
        return evaluateAndPersist(employee, date, today)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Cannot evaluate " + date + " yet - no punches recorded for today"));
    }

    private Optional<DailyAttendanceDetailResponse> evaluateAndPersist(Employee employee, LocalDate date, LocalDate today) {
        PipelineResult result = evaluatePipelineOrNull(employee, date, today);
        if (result == null) {
            return Optional.empty();
        }

        DailyAttendance record = dailyAttendanceRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), date)
                .orElseGet(() -> new DailyAttendance(employee, date, AttendanceStatus.ABSENT));
        record.applyDetail(result.detailStatus(), result.remarks(), result.leaveApplication(), result.tourRequest());
        record.setInTime(result.inTime());
        record.setOutTime(result.outTime());
        record.setTotalWorkingHours(result.totalWorkingHours());
        DailyAttendance saved = dailyAttendanceRepository.saveAndFlush(record);

        if (AUTO_DEBIT_TRIGGER_STATUSES.contains(saved.getDetailStatus())) {
            attendanceLeaveDeductionService.debitForUnauthorizedAttendance(employee, saved);
        }
        return Optional.of(DailyAttendanceDetailResponse.from(saved));
    }

    private record PipelineResult(AttendanceDetailStatus detailStatus, String remarks, LeaveApplication leaveApplication,
                                   TourRequest tourRequest, Instant inTime, Instant outTime, BigDecimal totalWorkingHours) {
    }

    private PipelineResult evaluatePipelineOrNull(Employee employee, LocalDate date, LocalDate today) {
        List<MobilePunch> punches = punchesForDate(employee.getId(), date);
        Optional<Instant> firstIn = punches.stream().filter(p -> p.getPunchType() == PunchType.IN)
                .map(MobilePunch::getPunchTime).findFirst();
        Optional<Instant> lastOut = punches.stream().filter(p -> p.getPunchType() == PunchType.OUT)
                .map(MobilePunch::getPunchTime).reduce((first, second) -> second);

        // Resolved once per day: HO/RO 5-day week vs. DPC 6-day week, and
        // (via employee_shift_schedule) any explicit roster override - see
        // ShiftResolutionService's javadoc. Needed by Step 1 (half-day leave
        // window), Step 2 (weekly-off), and Step 3 (on-time/late/hours
        // thresholds) below.
        ShiftDetails shiftDetails = shiftResolutionService.resolveShiftForEmployee(employee.getId(), date);

        // Step 1: sanctioned leave (CCS 1972 Rules) - resolved regardless of
        // punches/today-openness, since a day on leave has no "still open"
        // notion the way a working day's OUT punch does. Leave keeps
        // precedence over tour: an employee can't be simultaneously
        // sanctioned for leave and on tour for the same day.
        Optional<PipelineResult> leaveResult = evaluateLeave(employee, date, firstIn, lastOut, shiftDetails.shift());
        if (leaveResult.isPresent()) {
            return leaveResult.get();
        }

        // Step 1b: approved tour/official duty - also resolved regardless of
        // punches (a punch on a tour day is expected/allowed, not a
        // conflict - it just adds a "Tour + Punched" note to the remarks).
        Optional<PipelineResult> tourResult = evaluateTour(employee, date, !punches.isEmpty());
        if (tourResult.isPresent()) {
            return tourResult.get();
        }

        // Step 2: holiday / weekly-off, only when nothing was punched that day.
        if (punches.isEmpty()) {
            Optional<PipelineResult> calendarResult = evaluateCalendar(employee, date, shiftDetails);
            if (calendarResult.isPresent()) {
                return calendarResult.get();
            }
        }

        // Step 3 needs a closed day if it's today - can't judge lateness/
        // short-hours against a day that hasn't finished yet. But an IN
        // punch with no OUT yet is not "nothing happened" either - persist
        // a live IN_PROGRESS row (superseded once OUT is punched and this
        // branch stops applying) rather than silently skipping the day, so
        // a punch is reflected in daily_attendance/the history table right
        // away instead of only after the day closes.
        if (date.equals(today) && lastOut.isEmpty()) {
            if (firstIn.isEmpty()) {
                return null;
            }
            return new PipelineResult(AttendanceDetailStatus.IN_PROGRESS, "Punched in - day not yet closed out",
                    null, null, firstIn.get(), null, null);
        }
        return evaluatePunches(employee, date, firstIn, lastOut, shiftDetails.shift());
    }

    private List<MobilePunch> punchesForDate(Long employeeId, LocalDate date) {
        Instant start = date.atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
        return mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(employeeId, start, end);
    }

    private Optional<PipelineResult> evaluateLeave(Employee employee, LocalDate date, Optional<Instant> firstIn,
                                                     Optional<Instant> lastOut, ShiftMaster shift) {
        List<LeaveApplication> applications = leaveApplicationRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), LeaveApplicationStatus.APPROVED, date, date);
        if (applications.isEmpty()) {
            return Optional.empty();
        }
        LeaveApplication application = applications.get(0);

        if (application.getLeaveSession() == LeaveSession.FULL_DAY) {
            String remarks = "Sanctioned " + application.getLeaveType().getName();
            return Optional.of(new PipelineResult(AttendanceDetailStatus.ON_LEAVE, remarks, application, null,
                    firstIn.orElse(null), lastOut.orElse(null), null));
        }
        return Optional.of(evaluateHalfDayLeave(application, firstIn, lastOut, shift));
    }

    /**
     * 1st Half CL requires a punch 14:00-14:30 (afternoon attendance, a
     * fixed org-wide window independent of shift); 2nd Half CL requires a
     * punch within the resolved shift's own start-to-grace-cutoff window.
     * Only meaningful once Phase D lets leaveSession actually be set to
     * FIRST_HALF/SECOND_HALF when creating a leave application - this
     * branch is unreachable in practice until then, but implemented now so
     * the pipeline is complete rather than a stub.
     */
    private PipelineResult evaluateHalfDayLeave(LeaveApplication application, Optional<Instant> firstIn,
                                                 Optional<Instant> lastOut, ShiftMaster shift) {
        String leaveCode = application.getLeaveType().getCode();
        LocalTime windowStart = application.getLeaveSession() == LeaveSession.FIRST_HALF
                ? FIRST_HALF_PUNCH_WINDOW_START : shift.getStartTime();
        LocalTime windowEnd = application.getLeaveSession() == LeaveSession.FIRST_HALF
                ? FIRST_HALF_PUNCH_WINDOW_END : graceCutoff(shift);

        boolean punchedWithinRequiredWindow = firstIn.map(this::localTime)
                .map(time -> !time.isBefore(windowStart) && !time.isAfter(windowEnd))
                .orElse(false);
        long workedMinutes = workedMinutes(firstIn, lastOut);
        BigDecimal hours = toHours(workedMinutes);

        if (punchedWithinRequiredWindow && workedMinutes >= halfDayMinutes(shift)) {
            return new PipelineResult(AttendanceDetailStatus.HALF_DAY_PRESENT,
                    "Half Day Leave (0.5 " + leaveCode + ")", application, null, firstIn.orElse(null), lastOut.orElse(null), hours);
        }
        return new PipelineResult(AttendanceDetailStatus.HALF_DAY_SHORT,
                "Half Day Leave (0.5 " + leaveCode + ") - required punch session not met", application, null,
                firstIn.orElse(null), lastOut.orElse(null), hours);
    }

    private Optional<PipelineResult> evaluateTour(Employee employee, LocalDate date, boolean hasPunches) {
        List<TourRequest> tours = tourRequestRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), TourRequestStatus.APPROVED, date, date);
        if (tours.isEmpty()) {
            return Optional.empty();
        }
        TourRequest tour = tours.get(0);
        String remarks = "Official Tour / OD - Sanctioned to " + tour.getDestination()
                + (hasPunches ? " (Tour + Punched)" : "");
        return Optional.of(new PipelineResult(AttendanceDetailStatus.ON_TOUR, remarks, null, tour, null, null, null));
    }

    /**
     * V39 lets more than one Holiday row share a date (one per state, plus at
     * most one national/CENTRAL row) - a state-specific row only counts for
     * an employee actually posted in that state. See HolidayService's own
     * (independently-maintained) resolveState/isApplicable for the same
     * national-vs-state-match logic used by the ESS "my calendar" widget;
     * duplicated in miniature here rather than introducing a cross-service
     * dependency into this already-heavily-depended-on pipeline.
     */
    private Optional<PipelineResult> evaluateCalendar(Employee employee, LocalDate date, ShiftDetails shiftDetails) {
        List<Holiday> holidaysOnDate = holidayRepository.findByHolidayDate(date);
        String employeeState = resolveEmployeeState(employee);
        Optional<Holiday> holiday = holidaysOnDate.stream()
                .filter(h -> isApplicableToState(h, employeeState))
                .min(Comparator.comparingInt(h -> h.getHolidayType() == HolidayType.GAZETTED ? 0 : 1));
        if (holiday.isPresent()) {
            return Optional.of(new PipelineResult(AttendanceDetailStatus.HOLIDAY, holiday.get().getName(), null,
                    null, null, null, null));
        }
        if (shiftDetails.weeklyOff()) {
            return Optional.of(new PipelineResult(AttendanceDetailStatus.WEEKOFF, "Weekly off", null, null, null, null, null));
        }
        return Optional.empty();
    }

    private PipelineResult evaluatePunches(Employee employee, LocalDate date, Optional<Instant> firstInOpt,
                                            Optional<Instant> lastOutOpt, ShiftMaster shift) {
        if (firstInOpt.isEmpty() && lastOutOpt.isEmpty()) {
            return new PipelineResult(AttendanceDetailStatus.ABSENT, "No punches recorded", null, null, null, null, null);
        }

        long workedMinutes = workedMinutes(firstInOpt, lastOutOpt);
        BigDecimal hours = toHours(workedMinutes);
        Instant firstIn = firstInOpt.orElse(null);
        Instant lastOut = lastOutOpt.orElse(null);
        LocalTime firstInTime = firstInOpt.map(this::localTime).orElse(null);
        LocalTime lastOutTime = lastOutOpt.map(this::localTime).orElse(null);

        LocalTime standardStart = shift.getStartTime();
        LocalTime standardEnd = shift.getEndTime();
        LocalTime graceCutoff = graceCutoff(shift);
        LocalTime lateMaxLimit = lateMaxLimit(shift);
        LocalTime earlyDepartureMin = earlyDepartureMin(shift);
        int fullDayMinutes = fullDayMinutes(shift);
        int halfDayMinutes = halfDayMinutes(shift);

        boolean fullyCompliant = firstInTime != null && !firstInTime.isAfter(standardStart)
                && lastOutTime != null && !lastOutTime.isBefore(standardEnd)
                && workedMinutes >= fullDayMinutes;
        if (fullyCompliant) {
            return new PipelineResult(AttendanceDetailStatus.PRESENT, null, null, null, firstIn, lastOut, hours);
        }

        boolean inFlexGraceWindow = firstInTime != null && firstInTime.isAfter(standardStart) && !firstInTime.isAfter(graceCutoff);
        if (inFlexGraceWindow) {
            if (workedMinutes >= fullDayMinutes) {
                return new PipelineResult(AttendanceDetailStatus.GRACE_APPLIED, "Grace applied (full day met)", null, null,
                        firstIn, lastOut, hours);
            }
            return new PipelineResult(AttendanceDetailStatus.LATE_SHORT_HOURS, "Grace window used but full day not met",
                    null, null, firstIn, lastOut, hours);
        }

        boolean lateArrivalConcessionWindow = firstInTime != null && firstInTime.isAfter(graceCutoff) && !firstInTime.isAfter(lateMaxLimit);
        boolean earlyDepartureConcessionWindow = lastOutTime != null && !lastOutTime.isBefore(earlyDepartureMin) && lastOutTime.isBefore(standardEnd);
        if (lateArrivalConcessionWindow || earlyDepartureConcessionWindow) {
            return concessionResult(employee, date, firstIn, lastOut, hours);
        }

        boolean severelyOutOfWindow = (firstInTime != null && firstInTime.isAfter(lateMaxLimit))
                || (lastOutTime != null && lastOutTime.isBefore(earlyDepartureMin));
        if (severelyOutOfWindow) {
            AttendanceDetailStatus status = workedMinutes >= halfDayMinutes
                    ? AttendanceDetailStatus.HALF_DAY_ABSENT : AttendanceDetailStatus.ABSENT;
            return new PipelineResult(status, "Outside permitted attendance window", null, null, firstIn, lastOut, hours);
        }

        // Only reachable when clock times looked compliant/near-compliant but
        // total worked minutes couldn't be computed or fell short (e.g. a
        // missing OUT punch on a past day, or an unusually long unaccounted
        // gap between first-in and last-out) - the same ABSENT/HALF_DAY_ABSENT
        // bucket used above, since compliance can't be positively confirmed.
        AttendanceDetailStatus fallbackStatus = workedMinutes >= halfDayMinutes
                ? AttendanceDetailStatus.HALF_DAY_ABSENT : AttendanceDetailStatus.ABSENT;
        return new PipelineResult(fallbackStatus, "Insufficient working hours or incomplete punches", null, null,
                firstIn, lastOut, hours);
    }

    private PipelineResult concessionResult(Employee employee, LocalDate date, Instant firstIn, Instant lastOut, BigDecimal hours) {
        LocalDate monthStart = date.withDayOfMonth(1);
        long priorOccurrences = dailyAttendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
                employee.getId(), monthStart, date.minusDays(1), CONCESSION_STATUSES);
        if (priorOccurrences <= MONTHLY_CONCESSION_LIMIT) {
            return new PipelineResult(AttendanceDetailStatus.REQUIRES_REGULARIZATION,
                    "Concession eligible (prior occurrences this month: " + priorOccurrences + ") - requires HoD approval",
                    null, null, firstIn, lastOut, hours);
        }
        return new PipelineResult(AttendanceDetailStatus.UNAUTHORIZED_LATE,
                "Exceeded " + MONTHLY_CONCESSION_LIMIT + " monthly concessions (prior occurrences: " + priorOccurrences
                        + ") - marked for auto-debit", null, null, firstIn, lastOut, hours);
    }

    /** Mirrors HolidayService.resolveState (same JCI HO-in-Kolkata fallback), duplicated rather than shared - see evaluateCalendar's javadoc. */
    private String resolveEmployeeState(Employee employee) {
        DepartmentalPurchaseCentre dpc = employee.getDepartmentalPurchaseCentre();
        if (dpc != null) {
            return dpc.getState();
        }
        RegionalOffice ro = employee.getRegionalOffice();
        if (ro != null) {
            return ro.getState();
        }
        return HO_STATE;
    }

    private boolean isApplicableToState(Holiday holiday, String employeeState) {
        String state = holiday.getState();
        if (state == null || NATIONAL_STATE_MARKERS.contains(state.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return state.equalsIgnoreCase(employeeState);
    }

    private LocalTime localTime(Instant instant) {
        return instant.atZone(DISPLAY_ZONE).toLocalTime();
    }

    /**
     * Derives evaluatePunches' five time thresholds and two day-length
     * thresholds from one shift_master row, generalizing JCI Circular
     * JCI/HO/Pers/2024-25/53's own fixed HO/RO numbers (09:45 start, 30 min
     * grace -> 10:15 cutoff, a further 30 min late-arrival/early-departure
     * concession window -> 10:45/17:15, 18:15 end) into a formula every
     * shift shares: grace cutoff = start + grace; the concession window
     * extends another `grace` minutes past that on both ends. Verified to
     * reproduce the circular's own HO/RO numbers exactly at grace=30.
     */
    private LocalTime graceCutoff(ShiftMaster shift) {
        return shift.getStartTime().plusMinutes(shift.getGracePeriodMinutes());
    }

    private LocalTime lateMaxLimit(ShiftMaster shift) {
        return shift.getStartTime().plusMinutes(2L * shift.getGracePeriodMinutes());
    }

    private LocalTime earlyDepartureMin(ShiftMaster shift) {
        return shift.getEndTime().minusMinutes(2L * shift.getGracePeriodMinutes());
    }

    /** Falls back to the shift's own duration when full/half day minutes aren't configured (e.g. a watchmen rotation with no such policy defined yet). */
    private int fullDayMinutes(ShiftMaster shift) {
        return shift.getFullDayMinutes() != null ? shift.getFullDayMinutes() : shiftDurationMinutes(shift);
    }

    private int halfDayMinutes(ShiftMaster shift) {
        return shift.getHalfDayMinutes() != null ? shift.getHalfDayMinutes() : shiftDurationMinutes(shift) / 2;
    }

    private int shiftDurationMinutes(ShiftMaster shift) {
        long minutes = Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
        return (int) (minutes >= 0 ? minutes : minutes + Duration.ofDays(1).toMinutes());
    }

    private long workedMinutes(Optional<Instant> firstIn, Optional<Instant> lastOut) {
        if (firstIn.isEmpty() || lastOut.isEmpty()) {
            return 0;
        }
        return Duration.between(firstIn.get(), lastOut.get()).toMinutes();
    }

    private BigDecimal toHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
