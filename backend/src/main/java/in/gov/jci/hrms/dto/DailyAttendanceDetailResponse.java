package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.DailyAttendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The persisted, authoritative counterpart to MobilePunchService's
 * DailyAttendanceSummaryResponse (which stays non-persisted/on-the-fly and
 * unchanged) - this is what AttendanceAggregationService produces once a day
 * has actually been evaluated against leave/holiday/punch rules.
 */
public record DailyAttendanceDetailResponse(
        LocalDate date,
        String inTime,
        String outTime,
        BigDecimal totalWorkingHours,
        AttendanceDetailStatus detailStatus,
        String remarks,
        Long leaveApplicationId,
        String leaveTypeCode,
        Long tourRequestId,
        String tourRequestNumber,
        String tourDestination
) {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static DailyAttendanceDetailResponse from(DailyAttendance record) {
        return new DailyAttendanceDetailResponse(
                record.getAttendanceDate(),
                record.getInTime() != null ? record.getInTime().atZone(DISPLAY_ZONE).toLocalTime().format(TIME_FORMAT) : null,
                record.getOutTime() != null ? record.getOutTime().atZone(DISPLAY_ZONE).toLocalTime().format(TIME_FORMAT) : null,
                record.getTotalWorkingHours(),
                record.getDetailStatus(),
                record.getRemarks(),
                record.getLeaveApplication() != null ? record.getLeaveApplication().getId() : null,
                record.getLeaveApplication() != null ? record.getLeaveApplication().getLeaveType().getCode() : null,
                record.getTourRequest() != null ? record.getTourRequest().getId() : null,
                record.getTourRequest() != null ? record.getTourRequest().getRequestNumber() : null,
                record.getTourRequest() != null ? record.getTourRequest().getDestination() : null
        );
    }
}
