package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/**
 * One row per calendar day (Asia/Kolkata local date - see
 * MobilePunchService.getMyHistory), covering every day of the requested
 * month up to today (not just days with punches - a day with none is
 * ABSENT). inTime/outTime are "HH:mm:ss" strings, null on an ABSENT day.
 * serviceHours is a fully-formatted display string - "8.50 Hrs (8h 30m)"
 * once both in and out are known, the literal "In Progress" if only in is
 * known, or null on an ABSENT day.
 */
public record DailyAttendanceSummaryResponse(
        LocalDate date,
        String inTime,
        String outTime,
        String serviceHours,
        String status
) {
}
