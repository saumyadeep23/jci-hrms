package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/reports/pims/export. reportType is one of PimsReportType's
 * names; for AD_HOC, filter.columns() selects which vw_jci_employee_master_360
 * columns to render (see AdHocReportService.ALLOWED_COLUMNS).
 */
public record ReportExportRequest(
        @NotNull String reportType,
        @NotNull ExportFormat format,
        PimsReportFilter filter
) {
    public enum ExportFormat { XLSX, PDF }
}
