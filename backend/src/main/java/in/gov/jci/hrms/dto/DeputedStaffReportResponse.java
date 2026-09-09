package in.gov.jci.hrms.dto;

import java.util.List;

public record DeputedStaffReportResponse(
        List<DeputedStaffReportDto> rows,
        int totalDeputedOut,
        int totalDeputedIn,
        int dueForRepatriationThisQuarter
) {
}
