package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

public record AttendanceBreakdownResponse(
        int totalCycleDays,
        long presentDays,
        long halfDays,
        long absentDays,
        long onLeaveDays,
        long holidayDays,
        long weeklyOffDays,
        BigDecimal lopDays
) {
}
