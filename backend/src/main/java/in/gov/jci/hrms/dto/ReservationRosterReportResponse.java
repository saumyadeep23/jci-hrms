package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/reports/pims/reservation-roster. */
public record ReservationRosterReportResponse(
        List<ReservationRosterRow> rows,
        long pwbdCount,
        BigDecimal pwbdPercentage,
        long totalEmployees
) {
}
