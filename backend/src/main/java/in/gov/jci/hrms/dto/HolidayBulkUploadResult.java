package in.gov.jci.hrms.dto;

import java.util.List;

/** POST /api/v1/master/holidays/bulk-upload result - lenient per-row import (one bad row in an annual gazette shouldn't sink the whole file), so this reports what succeeded/failed rather than throwing on the first problem. */
public record HolidayBulkUploadResult(
        int totalRows,
        int createdRows,
        List<String> errors
) {
}
