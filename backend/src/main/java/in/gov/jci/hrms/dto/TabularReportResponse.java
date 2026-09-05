package in.gov.jci.hrms.dto;

import java.util.List;
import java.util.Map;

/**
 * Generic column/row projection shared by the Ad-Hoc Report Builder
 * (POST /api/v1/reports/pims/ad-hoc) and the export engine, which renders
 * whichever typed report was requested down to this same shape before
 * handing it to XlsxExportService/PdfExportService.
 */
public record TabularReportResponse(List<String> columns, List<Map<String, Object>> rows, long totalElements) {
}
