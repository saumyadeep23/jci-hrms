package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CcsForm1Response;
import in.gov.jci.hrms.dto.Circular53ComplianceRow;
import in.gov.jci.hrms.dto.EncashmentRegisterRow;
import in.gov.jci.hrms.dto.MusterRollRow;
import in.gov.jci.hrms.dto.PayrollCutoffFeedRow;
import in.gov.jci.hrms.service.AlmsReportExportService;
import in.gov.jci.hrms.service.AlmsReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * ALMS Enterprise Reporting and Analytics Workbench (Phase 4). The spec's
 * package name (com.jci.hrms.alms.dto.reports) doesn't match this codebase's
 * real convention (in.gov.jci.hrms.*, flat dto/ and service/ packages, no
 * report-specific subpackages anywhere else) - built under the real one.
 * /export returns a plain byte[] body (ResponseEntity<byte[]>), matching
 * PimsReportController's established export pattern, not the spec's
 * StreamingResponseBody (every report here is bounded/paged, not a
 * streaming-scale export).
 */
@RestController
@RequestMapping("/api/v1/reports/alms")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
public class AlmsReportController {

    private final AlmsReportService almsReportService;
    private final AlmsReportExportService almsReportExportService;

    public AlmsReportController(AlmsReportService almsReportService, AlmsReportExportService almsReportExportService) {
        this.almsReportService = almsReportService;
        this.almsReportExportService = almsReportExportService;
    }

    @GetMapping("/muster-roll")
    public Page<MusterRollRow> musterRoll(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                           @RequestParam(required = false) Long officeId,
                                           @RequestParam(required = false) String cadre, Pageable pageable) {
        return almsReportService.generateMusterRoll(startDate, endDate, officeId, cadre, pageable);
    }

    @GetMapping("/payroll-feed")
    public List<PayrollCutoffFeedRow> payrollFeed(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                                                   @RequestParam(required = false) Long officeId) {
        return almsReportService.generatePayrollFeed(periodStart, periodEnd, officeId);
    }

    @GetMapping("/circular53-concessions")
    public List<Circular53ComplianceRow> circular53(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
                                                      @RequestParam(required = false) Long officeId) {
        return almsReportService.generateCircular53Report(yearMonth, officeId);
    }

    @GetMapping("/ccs-form1")
    public CcsForm1Response ccsForm1(@RequestParam Long employeeId, @RequestParam int year) {
        return almsReportService.generateCcsForm1(employeeId, year);
    }

    @GetMapping("/encashment-register")
    public List<EncashmentRegisterRow> encashmentRegister(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return almsReportService.generateEncashmentRegister(fromDate, toDate);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam AlmsReportExportService.AlmsReportType reportType,
                                          @RequestParam AlmsReportExportService.ExportFormat format,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                          @RequestParam(required = false) Long officeId,
                                          @RequestParam(required = false) String cadre,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
                                          @RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) Integer year) {
        byte[] content = almsReportExportService.export(reportType, format, startDate, endDate, officeId, cadre, yearMonth, employeeId, year);
        MediaType mediaType = format == AlmsReportExportService.ExportFormat.XLSX
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        String extension = format == AlmsReportExportService.ExportFormat.XLSX ? "xlsx" : "csv";
        String filename = reportType.name().toLowerCase() + "-report." + extension;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
