package in.gov.jci.hrms.dto;

import java.util.List;

/** GET /api/v1/payroll/declarations/nps/admin/summary?fy={fy} - the two tabs of the HR/Bill Section dashboard for one financial year. */
public record NpsAdminSummaryResponse(
        String financialYear,
        List<NpsSubmittedRow> submitted,
        List<NpsPendingRow> pending
) {
}
