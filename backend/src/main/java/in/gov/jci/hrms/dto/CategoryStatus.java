package in.gov.jci.hrms.dto;

import java.util.List;

public record CategoryStatus(
        String category,
        long pendingCount,
        long promotedCount,
        long rejectedCount,
        List<RejectedRowSummary> rejectedRows
) {
}
