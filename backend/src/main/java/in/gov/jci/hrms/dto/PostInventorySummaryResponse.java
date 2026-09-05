package in.gov.jci.hrms.dto;

/** GET /api/v1/posts/summary - PIMS_SPEC.md Section 2.A's KPI metric bar. */
public record PostInventorySummaryResponse(
        long totalSanctioned,
        long occupied,
        long vacant,
        long frozen,
        long abolished
) {
}
