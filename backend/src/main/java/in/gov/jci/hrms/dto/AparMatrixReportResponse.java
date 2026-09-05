package in.gov.jci.hrms.dto;

import java.util.List;

/** GET /api/v1/reports/pims/apar-matrix. */
public record AparMatrixReportResponse(List<AparMatrixRow> rows, long dualChargeOver90DaysCount) {
}
