package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** POST /api/v1/reports/pims/ad-hoc - dynamic column selection over vw_jci_employee_master_360 (see AdHocReportService.ALLOWED_COLUMNS). */
public record AdHocReportRequest(
        @NotEmpty List<String> columns,
        PimsReportFilter filter,
        Integer page,
        Integer size
) {
}
