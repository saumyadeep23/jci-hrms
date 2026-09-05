package in.gov.jci.hrms.dto;

import java.util.List;

/** GET /api/v1/reports/pims/cadre-strength - Sanctioned/Occupied/Vacant/Frozen split across HO/RO/DPC. */
public record CadreStrengthReportResponse(List<CadreStrengthRow> byLocation, CadreStrengthRow overall) {
}
