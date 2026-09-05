package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AdHocReportRequest;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.ReportExportRequest;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** POST /api/v1/reports/pims/export - dispatches to the right report source, then renders via Xlsx/PdfExportService. */
@Service
public class ReportExportService {

    private final Map<PimsReportType, PimsReportExportSource> sources;
    private final AdHocReportService adHocReportService;
    private final XlsxExportService xlsxExportService;
    private final PdfExportService pdfExportService;

    public ReportExportService(List<PimsReportExportSource> exportSources, AdHocReportService adHocReportService,
                                XlsxExportService xlsxExportService, PdfExportService pdfExportService) {
        this.sources = new EnumMap<>(PimsReportType.class);
        exportSources.forEach(source -> sources.put(source.reportType(), source));
        this.adHocReportService = adHocReportService;
        this.xlsxExportService = xlsxExportService;
        this.pdfExportService = pdfExportService;
    }

    public byte[] export(ReportExportRequest request) {
        PimsReportType type;
        try {
            type = PimsReportType.valueOf(request.reportType());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Unknown report type: " + request.reportType());
        }

        PimsReportFilter filter = request.filter() != null ? request.filter() : PimsReportFilter.empty();
        TabularReportResponse data;
        String title;

        if (type == PimsReportType.AD_HOC) {
            if (filter.columns() == null || filter.columns().isEmpty()) {
                throw new BusinessRuleViolationException("Ad-hoc export requires at least one selected column");
            }
            data = adHocReportService.generate(new AdHocReportRequest(filter.columns(), filter, 0, 10_000));
            title = "Ad-Hoc Employee Report";
        } else {
            PimsReportExportSource source = sources.get(type);
            if (source == null) {
                throw new BusinessRuleViolationException("No export source registered for: " + type);
            }
            data = source.tabularData(filter);
            title = source.exportTitle();
        }

        return switch (request.format()) {
            case XLSX -> xlsxExportService.export(title, data);
            case PDF -> pdfExportService.export(title, data);
        };
    }
}
