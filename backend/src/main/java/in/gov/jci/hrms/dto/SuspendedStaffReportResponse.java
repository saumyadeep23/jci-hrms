package in.gov.jci.hrms.dto;

import java.util.List;

public record SuspendedStaffReportResponse(
        List<SuspendedStaffReportDto> rows,
        int totalUnderSuspension,
        int pendingNecThisMonth,
        int pending90DayReviews
) {
}
