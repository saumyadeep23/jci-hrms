package in.gov.jci.hrms.dto;

import java.util.List;

/**
 * GET /api/v1/reports/pims/superannuation - forecasts retirements using
 * employee_superannuation_details.superannuation_date, which the DB itself
 * maintains (fn_calculate_jci_superannuation_date trigger: 58 years for
 * Regular cadre with a last-day-of-month rule, 60 years / 5-year tenure
 * cap for Board Directors - see V31 migration). entries is scoped to the
 * requested window (PimsReportFilter.months, default 60); windowSummary
 * always reports all five standard windows (6/12/24/36/60) for the KPI ribbon.
 */
public record SuperannuationReportResponse(List<SuperannuationWindowSummary> windowSummary, List<SuperannuationForecastEntry> entries) {
}
