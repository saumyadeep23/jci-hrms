package in.gov.jci.hrms.dto;

import java.util.List;

/**
 * Shared filter bag for the PIMS reporting hub (GET query params on the
 * typed report endpoints, and the body of POST /api/v1/reports/pims/export).
 * Every field is optional - each report service only reads the ones
 * relevant to it and ignores the rest.
 */
public record PimsReportFilter(
        Long departmentId,
        Long designationId,
        Long roId,
        Long dpcId,
        String employmentCategory,
        String socialCategory,
        String recruitmentMode,
        /** Superannuation report's forecast window, in months from today. */
        Integer months,
        /** Ad-hoc report builder's selected columns from vw_jci_employee_master_360 (see AdHocReportService.ALLOWED_COLUMNS). */
        List<String> columns,
        String search,
        /** Increment Due List's "Increment Month" filter (1-12, null = All Months) - matched against the employee's date-of-joining anniversary month, since that's what actually drives an individual REGULAR employee's annual increment date in this system. */
        Integer incrementMonth
) {
    public static PimsReportFilter empty() {
        return new PimsReportFilter(null, null, null, null, null, null, null, null, null, null, null);
    }
}
