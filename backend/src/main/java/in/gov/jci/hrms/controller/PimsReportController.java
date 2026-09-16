package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AdHocReportRequest;
import in.gov.jci.hrms.dto.AparMatrixReportResponse;
import in.gov.jci.hrms.dto.CadreStrengthReportResponse;
import in.gov.jci.hrms.dto.DeputedStaffReportResponse;
import in.gov.jci.hrms.dto.Manpower4TierReportResponse;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.ReportExportRequest;
import in.gov.jci.hrms.dto.ReservationRosterReportResponse;
import in.gov.jci.hrms.dto.SuperannuationReportResponse;
import in.gov.jci.hrms.dto.SuspendedStaffReportResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.service.AdHocReportService;
import in.gov.jci.hrms.service.AparMatrixReportService;
import in.gov.jci.hrms.service.CadreStrengthReportService;
import in.gov.jci.hrms.service.DeputedStaffReportService;
import in.gov.jci.hrms.service.Manpower4TierReportService;
import in.gov.jci.hrms.service.ReportExportService;
import in.gov.jci.hrms.service.ReservationRosterReportService;
import in.gov.jci.hrms.service.SuperannuationReportService;
import in.gov.jci.hrms.service.SuspendedStaffReportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PIMS_SPEC.md reporting-hub task, Sections 2 and 3. */
@RestController
@RequestMapping("/api/v1/reports/pims")
@PreAuthorize("hasRole('HR_ADMIN')")
public class PimsReportController {

    private final CadreStrengthReportService cadreStrengthReportService;
    private final SuperannuationReportService superannuationReportService;
    private final ReservationRosterReportService reservationRosterReportService;
    private final Manpower4TierReportService manpower4TierReportService;
    private final AparMatrixReportService aparMatrixReportService;
    private final AdHocReportService adHocReportService;
    private final ReportExportService reportExportService;
    private final DeputedStaffReportService deputedStaffReportService;
    private final SuspendedStaffReportService suspendedStaffReportService;

    public PimsReportController(CadreStrengthReportService cadreStrengthReportService,
                                 SuperannuationReportService superannuationReportService,
                                 ReservationRosterReportService reservationRosterReportService,
                                 Manpower4TierReportService manpower4TierReportService,
                                 AparMatrixReportService aparMatrixReportService,
                                 AdHocReportService adHocReportService,
                                 ReportExportService reportExportService,
                                 DeputedStaffReportService deputedStaffReportService,
                                 SuspendedStaffReportService suspendedStaffReportService) {
        this.cadreStrengthReportService = cadreStrengthReportService;
        this.superannuationReportService = superannuationReportService;
        this.reservationRosterReportService = reservationRosterReportService;
        this.manpower4TierReportService = manpower4TierReportService;
        this.aparMatrixReportService = aparMatrixReportService;
        this.adHocReportService = adHocReportService;
        this.reportExportService = reportExportService;
        this.deputedStaffReportService = deputedStaffReportService;
        this.suspendedStaffReportService = suspendedStaffReportService;
    }

    @GetMapping("/cadre-strength")
    public CadreStrengthReportResponse cadreStrength(@RequestParam(required = false) Long departmentId,
                                                       @RequestParam(required = false) Long roId,
                                                       @RequestParam(required = false) Long dpcId) {
        return cadreStrengthReportService.generate(filter(departmentId, null, roId, dpcId, null, null, null, null));
    }

    @GetMapping("/superannuation")
    public SuperannuationReportResponse superannuation(@RequestParam(required = false) Long departmentId,
                                                         @RequestParam(required = false) Integer months) {
        return superannuationReportService.generate(filter(departmentId, null, null, null, null, null, months, null));
    }

    @GetMapping("/reservation-roster")
    public ReservationRosterReportResponse reservationRoster(@RequestParam(required = false) Long departmentId) {
        return reservationRosterReportService.generate(filter(departmentId, null, null, null, null, null, null, null));
    }

    @GetMapping("/manpower-4tier")
    public Manpower4TierReportResponse manpower4Tier(@RequestParam(required = false) Long departmentId) {
        return manpower4TierReportService.generate(filter(departmentId, null, null, null, null, null, null, null));
    }

    @GetMapping("/apar-matrix")
    public AparMatrixReportResponse aparMatrix(@RequestParam(required = false) Long departmentId) {
        return aparMatrixReportService.generate(filter(departmentId, null, null, null, null, null, null, null));
    }

    /** Base path corrected from the spec's assumed /api/v1/pims/reports/... to this controller's real /api/v1/reports/pims/... convention. Export is via the existing generic POST .../export (reportType=DEPUTED_STAFF), not a bespoke GET .../export endpoint - see DeputedStaffReportService's own javadoc. */
    @GetMapping("/deputed-staff")
    public DeputedStaffReportResponse deputedStaff(@RequestParam(required = false) String direction,
                                                     @RequestParam(required = false) String organizationType,
                                                     @RequestParam(required = false) String station,
                                                     @RequestParam(required = false) String search) {
        return deputedStaffReportService.generate(direction, organizationType, station, search);
    }

    /** See deputedStaff()'s own comment re: base path and export. */
    @GetMapping("/suspended-staff")
    public SuspendedStaffReportResponse suspendedStaff(@RequestParam(required = false) String subsistencePercentage,
                                                         @RequestParam(required = false) String necStatus,
                                                         @RequestParam(required = false) String station,
                                                         @RequestParam(required = false) String search) {
        return suspendedStaffReportService.generate(subsistencePercentage, necStatus, station, search);
    }

    @PostMapping("/ad-hoc")
    public TabularReportResponse adHoc(@Valid @RequestBody AdHocReportRequest request) {
        return adHocReportService.generate(request);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ReportExportRequest request) {
        byte[] content = reportExportService.export(request);
        MediaType mediaType = request.format() == ReportExportRequest.ExportFormat.PDF
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String extension = request.format() == ReportExportRequest.ExportFormat.PDF ? "pdf" : "xlsx";
        String filename = request.reportType().toLowerCase() + "-report." + extension;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private PimsReportFilter filter(Long departmentId, Long designationId, Long roId, Long dpcId,
                                     String employmentCategory, String socialCategory, Integer months, java.util.List<String> columns) {
        return new PimsReportFilter(departmentId, designationId, roId, dpcId, employmentCategory, socialCategory, null, months, columns, null, null);
    }
}
