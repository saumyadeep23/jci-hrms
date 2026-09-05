package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CCS(Leave) Rules 1972-style validation, invoked from LeaveApplicationService
 * at create() time only (not re-checked at submit/approve - a later change to
 * adjacent leave or the holiday calendar won't retroactively invalidate an
 * already-created application; flagged as a known limitation, not fixed here).
 *
 * noRollbackFor is required here, not cosmetic: LeaveApplicationService.preview()
 * calls this from within its own (class-level readOnly) transaction and catches
 * BusinessRuleViolationException to turn it into a normal "valid: false" return
 * value. Without noRollbackFor, Spring's transaction interceptor marks the
 * shared transaction rollback-only the instant the exception crosses THIS
 * class's transactional boundary - before preview()'s try/catch ever runs -
 * so preview() returning normally afterwards still fails at commit time with
 * UnexpectedRollbackException. create() is unaffected: it never catches the
 * exception, so it keeps propagating out through LeaveApplicationService's own
 * (still default-rollback) transactional boundary exactly as before.
 */
@Service
@Transactional(readOnly = true, noRollbackFor = BusinessRuleViolationException.class)
public class LeaveValidationService {

    private static final int MAX_CONTINUOUS_ABSENCE_YEARS = 5;
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    /** Tolerance for comparing a client-supplied totalDays against the server-computed figure, to absorb BigDecimal scale/rounding noise, not genuine mismatches. */
    private static final BigDecimal TOTAL_DAYS_EPSILON = new BigDecimal("0.05");
    private static final Set<String> CONTIGUOUS_RESTRICTED_CODES = Set.of("EL", "HPL", "CCL");

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final HolidayRepository holidayRepository;

    public LeaveValidationService(LeaveApplicationRepository leaveApplicationRepository, HolidayRepository holidayRepository) {
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.holidayRepository = holidayRepository;
    }

    /**
     * Runs every structural CCS rule (half-day-session eligibility, 5-year
     * continuous-absence cap, contiguous-CL restriction) and returns the
     * authoritative debitable-days figure - the number of days this
     * application actually costs its own leave type's balance (before any
     * Commuted-Leave-style 2x-HPL conversion, which is a separate concern -
     * see LeaveApplicationService.resolveDebitTarget). Throws
     * BusinessRuleViolationException on any violation.
     */
    public BigDecimal validateAndComputeDebitableDays(Employee employee, LeaveType leaveType, LocalDate startDate,
                                                        LocalDate endDate, LeaveSession leaveSession) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleViolationException("endDate must not be before startDate");
        }
        validateHalfDaySessionRestrictedToCl(leaveType, leaveSession, startDate, endDate);
        validateMaxContinuousAbsence(startDate, endDate);
        validateNoContiguousClAndStatutoryLeave(employee, leaveType, startDate, endDate);

        return computeDebitableDays(leaveType, startDate, endDate, leaveSession);
    }

    /** Separate from validateAndComputeDebitableDays so a dry-run preview (which doesn't have a client-supplied totalDays to check against) can skip this. */
    public void validateTotalDaysMatches(BigDecimal computedDebitableDays, BigDecimal clientSuppliedTotalDays) {
        if (clientSuppliedTotalDays.subtract(computedDebitableDays).abs().compareTo(TOTAL_DAYS_EPSILON) > 0) {
            throw new BusinessRuleViolationException(
                    "totalDays (" + clientSuppliedTotalDays + ") does not match the computed debitable days ("
                            + computedDebitableDays + ") for this leave type and date range under CCS 1972 Rules");
        }
    }

    private void validateHalfDaySessionRestrictedToCl(LeaveType leaveType, LeaveSession leaveSession,
                                                        LocalDate startDate, LocalDate endDate) {
        if (leaveSession == LeaveSession.FULL_DAY) {
            return;
        }
        if (!"CL".equals(leaveType.getCode())) {
            throw new BusinessRuleViolationException("Half-day sessions (1st/2nd Half) are only permitted for CL");
        }
        if (!startDate.equals(endDate)) {
            throw new BusinessRuleViolationException("A half-day CL application must have startDate equal to endDate");
        }
    }

    private void validateMaxContinuousAbsence(LocalDate startDate, LocalDate endDate) {
        if (endDate.isAfter(startDate.plusYears(MAX_CONTINUOUS_ABSENCE_YEARS))) {
            throw new BusinessRuleViolationException(
                    "Continuous leave cannot exceed " + MAX_CONTINUOUS_ABSENCE_YEARS + " years");
        }
    }

    /**
     * CL cannot be applied contiguously (no gap) immediately before or after
     * EL, HPL, or CCL - checked in both directions, against currently
     * APPROVED applications only (a DRAFT/PENDING/REJECTED/CANCELLED
     * neighbor doesn't block).
     */
    private void validateNoContiguousClAndStatutoryLeave(Employee employee, LeaveType leaveType, LocalDate startDate,
                                                           LocalDate endDate) {
        boolean newIsCl = "CL".equals(leaveType.getCode());
        boolean newIsRestricted = CONTIGUOUS_RESTRICTED_CODES.contains(leaveType.getCode());
        if (!newIsCl && !newIsRestricted) {
            return;
        }

        List<LeaveApplication> adjacent = new ArrayList<>();
        adjacent.addAll(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                employee.getId(), LeaveApplicationStatus.APPROVED, startDate.minusDays(1)));
        adjacent.addAll(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(
                employee.getId(), LeaveApplicationStatus.APPROVED, endDate.plusDays(1)));

        for (LeaveApplication existing : adjacent) {
            String existingCode = existing.getLeaveType().getCode();
            if (newIsCl && CONTIGUOUS_RESTRICTED_CODES.contains(existingCode)) {
                throw new BusinessRuleViolationException("CL cannot be applied contiguously before/after " + existingCode);
            }
            if (newIsRestricted && "CL".equals(existingCode)) {
                throw new BusinessRuleViolationException(leaveType.getCode() + " cannot be applied contiguously before/after CL");
            }
        }
    }

    /**
     * CL excludes intervening Saturdays/Sundays/Gazetted holidays from the
     * debit; every other leave type (EL, HPL, Commuted, CCL, and anything
     * else not explicitly called out as CL-like) includes them - i.e. debits
     * the full calendar-day span. A half-day session is always exactly 0.5,
     * regardless of which weekday it falls on.
     */
    private BigDecimal computeDebitableDays(LeaveType leaveType, LocalDate startDate, LocalDate endDate, LeaveSession leaveSession) {
        if (leaveSession != LeaveSession.FULL_DAY) {
            return HALF_DAY;
        }
        if ("CL".equals(leaveType.getCode())) {
            return countExcludingWeekendsAndHolidays(startDate, endDate);
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private BigDecimal countExcludingWeekendsAndHolidays(LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetween(startDate, endDate).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        long count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            if (!isWeekend && !holidays.contains(date)) {
                count++;
            }
        }
        return BigDecimal.valueOf(count);
    }
}
