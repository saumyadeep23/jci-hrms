package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DailyAttendanceSummaryResponse;
import in.gov.jci.hrms.dto.MobilePunchRequest;
import in.gov.jci.hrms.dto.MobilePunchResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.MobilePunch;
import in.gov.jci.hrms.entity.PunchType;
import in.gov.jci.hrms.entity.ReviewStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.MobilePunchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MobilePunchService {

    /** All employees are physically in India (ap-south-1/JCI's own offices) - punches are grouped/displayed in IST regardless of which UTC offset the submitting device's clock happened to be in. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(MobilePunchService.class);

    private final MobilePunchRepository mobilePunchRepository;
    private final EmployeeRepository employeeRepository;
    private final GeofenceService geofenceService;
    private final AttendanceAggregationService attendanceAggregationService;
    private final Clock clock;

    /**
     * @Autowired is required here, not optional - with two constructors and
     * neither marked, Spring can't pick one and falls back to looking for a
     * no-arg constructor, which doesn't exist (NoSuchMethodException:
     * MobilePunchService.<init>() at boot). None of this class's Mockito
     * unit tests catch that, since they all construct the service directly
     * with `new`, never going through Spring's DI container.
     */
    @Autowired
    public MobilePunchService(MobilePunchRepository mobilePunchRepository, EmployeeRepository employeeRepository,
                               GeofenceService geofenceService, AttendanceAggregationService attendanceAggregationService) {
        this(mobilePunchRepository, employeeRepository, geofenceService, attendanceAggregationService, Clock.system(DISPLAY_ZONE));
    }

    /** Package-visible so tests can pin "today" - getMyHistory's day-enumeration is otherwise genuinely wall-clock-dependent. */
    MobilePunchService(MobilePunchRepository mobilePunchRepository, EmployeeRepository employeeRepository,
                        GeofenceService geofenceService, AttendanceAggregationService attendanceAggregationService, Clock clock) {
        this.mobilePunchRepository = mobilePunchRepository;
        this.employeeRepository = employeeRepository;
        this.geofenceService = geofenceService;
        this.attendanceAggregationService = attendanceAggregationService;
        this.clock = clock;
    }

    @Transactional
    public MobilePunchResponse create(MobilePunchRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        boolean withinGeofence = geofenceService.isWithinGeofence(employee, request.latitude(), request.longitude());
        ReviewStatus reviewStatus = withinGeofence ? ReviewStatus.VALID : ReviewStatus.FLAGGED_FOR_REVIEW;

        MobilePunch punch = new MobilePunch(employee, request.punchTime(), request.punchType(),
                request.latitude(), request.longitude(), withinGeofence, reviewStatus);
        punch.setAccuracyMeters(request.accuracyMeters());
        punch.setDeviceId(request.deviceId());
        punch.setPhotoS3Key(request.photoS3Key());

        MobilePunch saved = mobilePunchRepository.saveAndFlush(punch);
        scheduleDailyAttendanceSync(employee.getId(), request.punchTime());
        return MobilePunchResponse.from(saved);
    }

    /**
     * Upserts daily_attendance for the punch's day immediately, rather than
     * leaving that day's row to only be created/refreshed whenever someone
     * next happens to view the attendance history table. Best-effort: a
     * failure here must never roll back or fail the punch itself (the punch
     * write above is what matters; daily_attendance is a derived, re-runnable
     * projection of it - see AttendanceAggregationService's class javadoc).
     *
     * Deferred to run in an afterCommit() synchronization, in its own
     * (REQUIRES_NEW) transaction, rather than being called inline from this
     * still-open transaction: evaluateDay() re-reads today's punches from
     * the database to compute its result, and under READ COMMITTED a
     * separate transaction started before this one commits can't see the
     * punch just saveAndFlush()'d above - it would always fail with
     * "no punches recorded for today" for the day's first punch. Running
     * after commit guarantees evaluateDay() sees this punch; the try/catch
     * inside stays there so a sync failure still never escapes to the
     * caller, even post-commit.
     *
     * Falls back to running immediately, inline, when no Spring transaction
     * synchronization is active - registerSynchronization() requires one and
     * throws IllegalStateException otherwise. Only relevant when create() is
     * invoked outside its normal @Transactional proxy (e.g. this class's own
     * Mockito unit tests construct it with `new`, bypassing the proxy
     * entirely); in real use through Spring, synchronization is always
     * active here.
     */
    private void scheduleDailyAttendanceSync(Long employeeId, Instant punchTime) {
        LocalDate punchDate = punchTime.atZone(DISPLAY_ZONE).toLocalDate();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            syncDailyAttendance(employeeId, punchDate);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncDailyAttendance(employeeId, punchDate);
            }
        });
    }

    private void syncDailyAttendance(Long employeeId, LocalDate punchDate) {
        try {
            attendanceAggregationService.evaluateDay(employeeId, punchDate);
        } catch (RuntimeException e) {
            log.warn("Could not sync daily_attendance for employee {} on {} after punch: {}",
                    employeeId, punchDate, e.getMessage());
        }
    }

    public MobilePunchResponse getById(Long id) {
        return MobilePunchResponse.from(findOrThrow(id));
    }

    public Page<MobilePunchResponse> list(Pageable pageable) {
        return mobilePunchRepository.findAll(pageable).map(MobilePunchResponse::from);
    }

    /**
     * Aggregates a month's raw punches into one row per calendar day, from
     * day 1 through today (a future month/day hasn't happened yet, so it's
     * simply not included rather than being marked ABSENT). A day with no
     * punches at all is ABSENT - note this has no idea which days are
     * weekly-offs or holidays, since no working-day/leave calendar is
     * reachable from here, so Saturdays/Sundays currently show as ABSENT
     * too. Each present day uses first IN and last OUT (regardless of how
     * many IN/OUT pairs happened - e.g. a lunch break produces extra
     * punches that don't change the headline in/out).
     */
    public List<DailyAttendanceSummaryResponse> getMyHistory(Long employeeId, int year, int month) {
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
        LocalDate lastDayToShow = yearMonth.equals(currentYearMonth) ? today : yearMonth.atEndOfMonth();

        Instant start = yearMonth.atDay(1).atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(DISPLAY_ZONE).toInstant();

        List<MobilePunch> punches =
                mobilePunchRepository.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(employeeId, start, end);

        Map<LocalDate, List<MobilePunch>> byDate = punches.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getPunchTime().atZone(DISPLAY_ZONE).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DailyAttendanceSummaryResponse> result = new ArrayList<>();
        for (LocalDate date = yearMonth.atDay(1); !date.isAfter(lastDayToShow); date = date.plusDays(1)) {
            result.add(summarizeDay(date, byDate.getOrDefault(date, List.of())));
        }
        return result;
    }

    private DailyAttendanceSummaryResponse summarizeDay(LocalDate date, List<MobilePunch> dayPunches) {
        if (dayPunches.isEmpty()) {
            return new DailyAttendanceSummaryResponse(date, null, null, null, "ABSENT");
        }

        Optional<MobilePunch> firstIn = dayPunches.stream().filter(p -> p.getPunchType() == PunchType.IN).findFirst();
        Optional<MobilePunch> lastOut = dayPunches.stream()
                .filter(p -> p.getPunchType() == PunchType.OUT)
                .reduce((first, second) -> second);

        String inTime = firstIn.map(p -> formatTime(p.getPunchTime())).orElse(null);
        String outTime = lastOut.map(p -> formatTime(p.getPunchTime())).orElse(null);

        if (firstIn.isPresent() && lastOut.isPresent()) {
            Duration worked = Duration.between(firstIn.get().getPunchTime(), lastOut.get().getPunchTime());
            double hours = worked.toMinutes() / 60.0;
            String serviceHours = String.format("%.2f Hrs (%dh %dm)", hours, worked.toHours(), worked.toMinutesPart());
            return new DailyAttendanceSummaryResponse(date, inTime, outTime, serviceHours, "PRESENT");
        }
        // Punched in but not out yet (or, as an edge case, only an orphan OUT
        // with no IN that day) - either way there's no complete pair to
        // compute a duration from.
        return new DailyAttendanceSummaryResponse(date, inTime, outTime, "In Progress", "IN_PROGRESS");
    }

    private String formatTime(Instant instant) {
        return instant.atZone(DISPLAY_ZONE).toLocalTime().format(TIME_FORMAT);
    }

    private MobilePunch findOrThrow(Long id) {
        return mobilePunchRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Mobile Punch", id));
    }
}
