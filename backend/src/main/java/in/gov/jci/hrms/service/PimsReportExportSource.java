package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;

/** Implemented by every PIMS reporting-hub report service so ReportExportService can render any of them to XLSX/PDF generically. */
public interface PimsReportExportSource {

    PimsReportType reportType();

    /** Human-readable title for the export's header block/sheet name. */
    String exportTitle();

    TabularReportResponse tabularData(PimsReportFilter filter);
}
