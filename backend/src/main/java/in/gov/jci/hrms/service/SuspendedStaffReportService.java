package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.SuspendedStaffReportDto;
import in.gov.jci.hrms.dto.SuspendedStaffReportResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PIMS Reporting Hub "Suspended Staff" tab - GET /api/v1/reports/pims/suspended-staff. */
@Service
public class SuspendedStaffReportService implements PimsReportExportSource {

    private static final int REVIEW_DUE_DAYS = 90;

    private final EmployeeSuspensionRecordRepository suspensionRepository;
    private final EmployeeSuspensionNecRepository necRepository;

    public SuspendedStaffReportService(EmployeeSuspensionRecordRepository suspensionRepository, EmployeeSuspensionNecRepository necRepository) {
        this.suspensionRepository = suspensionRepository;
        this.necRepository = necRepository;
    }

    public SuspendedStaffReportResponse generate(String subsistencePercentage, String necStatus, String station, String search) {
        LocalDate today = LocalDate.now();
        List<EmployeeSuspensionRecord> all = suspensionRepository.findActiveSuspensionsWithNecStatus(today.getMonthValue(), today.getYear());

        String stationFilter = station != null && !station.isBlank() ? station.toLowerCase() : null;
        String searchFilter = search != null && !search.isBlank() ? search.toLowerCase() : null;

        List<SuspendedStaffReportDto> rows = all.stream()
                .map(s -> toDto(s, today))
                .filter(r -> subsistencePercentage == null || subsistencePercentage.isBlank()
                        || r.currentSubsistencePercentage().stripTrailingZeros().equals(new java.math.BigDecimal(subsistencePercentage).stripTrailingZeros()))
                .filter(r -> necStatus == null || necStatus.isBlank() || necStatus.equalsIgnoreCase("ALL") || r.currentMonthNecStatus().equalsIgnoreCase(necStatus))
                .filter(r -> stationFilter == null || r.hqStation().toLowerCase().contains(stationFilter))
                .filter(r -> searchFilter == null || r.empCode().toLowerCase().contains(searchFilter) || r.employeeName().toLowerCase().contains(searchFilter))
                .toList();

        int pendingNec = (int) rows.stream().filter(r -> !"VERIFIED".equals(r.currentMonthNecStatus())).count();
        int pendingReviews = (int) rows.stream().filter(SuspendedStaffReportDto::isReviewOverdue).count();

        return new SuspendedStaffReportResponse(rows, rows.size(), pendingNec, pendingReviews);
    }

    private SuspendedStaffReportDto toDto(EmployeeSuspensionRecord s, LocalDate today) {
        Employee employee = s.getEmployee();
        long daysUnderSuspension = ChronoUnit.DAYS.between(s.getEffectiveFrom(), today);
        boolean reviewOverdue = daysUnderSuspension > REVIEW_DUE_DAYS && s.getReviewDate() == null;

        String necStatus = necRepository.findBySuspension_IdAndSalMonthAndSalYear(s.getId(), today.getMonthValue(), today.getYear())
                .map(nec -> nec.isVerified() ? "VERIFIED" : "PENDING_VERIFICATION")
                .orElse("NOT_SUBMITTED");

        return new SuspendedStaffReportDto(
                employee.getId(), employee.getEmployeeCode(), employee.getFullName(), cadreOf(employee),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                s.getHqStation(), s.getSuspensionOrderNo(), s.getSuspensionOrderDate(), s.getEffectiveFrom(),
                daysUnderSuspension, s.getCurrentSubsistencePercentage(), reviewOverdue, necStatus,
                s.getStatus(), s.getRegularizationType()
        );
    }

    private String cadreOf(Employee employee) {
        Designation designation = employee.getDesignation();
        if (designation == null) {
            return null;
        }
        GradeScaleMaster gradeScale = designation.getGradeScale();
        return gradeScale != null && gradeScale.getCadre() != null ? gradeScale.getCadre().name() : null;
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.SUSPENDED_STAFF;
    }

    @Override
    public String exportTitle() {
        return "Suspended Staff Register";
    }

    /** Export ignores subsistence-rate/NEC-status/station (not part of the shared PimsReportFilter) - only filter.search() is honored, same precedent as SeparatedEmployeeDirectoryService. */
    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        SuspendedStaffReportResponse data = generate(null, null, null, filter != null ? filter.search() : null);
        List<Map<String, Object>> tableRows = data.rows().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Emp Code", r.empCode());
            row.put("Name", r.employeeName());
            row.put("Cadre", r.cadre());
            row.put("Designation", r.designation());
            row.put("HQ Station", r.hqStation());
            row.put("Suspension Order No", r.suspensionOrderNo());
            row.put("Effective From", r.effectiveFrom());
            row.put("Days Under Suspension", r.daysUnderSuspension());
            row.put("Subsistence %", r.currentSubsistencePercentage());
            row.put("Review Overdue", r.isReviewOverdue());
            row.put("Current Month NEC Status", r.currentMonthNecStatus());
            row.put("Status", r.status());
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Emp Code", "Name", "Cadre", "Designation", "HQ Station", "Suspension Order No", "Effective From",
                        "Days Under Suspension", "Subsistence %", "Review Overdue", "Current Month NEC Status", "Status"),
                tableRows, tableRows.size());
    }
}
