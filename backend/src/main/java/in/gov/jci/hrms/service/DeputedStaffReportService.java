package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeputedStaffReportDto;
import in.gov.jci.hrms.dto.DeputedStaffReportResponse;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PIMS Reporting Hub "Deputed Staff" tab - GET /api/v1/reports/pims/deputed-staff. */
@Service
public class DeputedStaffReportService implements PimsReportExportSource {

    private final EmployeeDeputationRecordRepository deputationRepository;

    public DeputedStaffReportService(EmployeeDeputationRecordRepository deputationRepository) {
        this.deputationRepository = deputationRepository;
    }

    public DeputedStaffReportResponse generate(String direction, String organizationType, String station, String search) {
        LocalDate today = LocalDate.now();
        List<EmployeeDeputationRecord> all = deputationRepository.findAllActiveDeputations(today);

        String directionFilter = direction != null && !direction.isBlank() && !direction.equalsIgnoreCase("ALL") ? direction.toUpperCase() : null;
        String orgTypeFilter = organizationType != null && !organizationType.isBlank() ? organizationType.toLowerCase() : null;
        String stationFilter = station != null && !station.isBlank() ? station.toLowerCase() : null;
        String searchFilter = search != null && !search.isBlank() ? search.toLowerCase() : null;

        List<DeputedStaffReportDto> rows = all.stream()
                .filter(d -> directionFilter == null || d.getDeputationDirection().name().equals(directionFilter))
                .filter(d -> orgTypeFilter == null || d.getOrganizationType().toLowerCase().contains(orgTypeFilter))
                .filter(d -> stationFilter == null || d.getPostingStation().toLowerCase().contains(stationFilter))
                .filter(d -> searchFilter == null
                        || d.getEmployee().getEmployeeCode().toLowerCase().contains(searchFilter)
                        || d.getEmployee().getFullName().toLowerCase().contains(searchFilter))
                .map(this::toDto)
                .toList();

        int totalDeputedOut = (int) all.stream().filter(d -> d.getDeputationDirection() == DeputationDirection.DEPUTATION_OUT).count();
        int totalDeputedIn = (int) all.stream().filter(d -> d.getDeputationDirection() == DeputationDirection.DEPUTATION_IN).count();
        LocalDate quarterEnd = today.plusMonths(3);
        int dueForRepatriation = (int) all.stream()
                .map(d -> d.getExtensionValidUpTo() != null ? d.getExtensionValidUpTo() : d.getPeriodTo())
                .filter(end -> !end.isBefore(today) && !end.isAfter(quarterEnd))
                .count();

        return new DeputedStaffReportResponse(rows, totalDeputedOut, totalDeputedIn, dueForRepatriation);
    }

    private DeputedStaffReportDto toDto(EmployeeDeputationRecord d) {
        Employee employee = d.getEmployee();
        return new DeputedStaffReportDto(
                employee.getId(), employee.getEmployeeCode(), employee.getFullName(), cadreOf(employee),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                d.getDeputationDirection(), d.getOrganizationName(), d.getOrganizationType(), d.getPostingStation(),
                d.isSameStation(), d.getPeriodFrom(), d.getPeriodTo(), d.getExtensionValidUpTo(), d.getPayOption(),
                d.getDeputationAllowanceRate(), d.getDeputationAllowanceCap(), d.isLspcApplicable(), d.getLspcBorneBy(),
                d.getLspcMonthlyRate(), d.getStatus()
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
        return PimsReportType.DEPUTED_STAFF;
    }

    @Override
    public String exportTitle() {
        return "Deputed Staff Register";
    }

    /** Export ignores direction/organizationType/station (not part of the shared PimsReportFilter) - only filter.search() is honored, same precedent as SeparatedEmployeeDirectoryService. */
    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        DeputedStaffReportResponse data = generate(null, null, null, filter != null ? filter.search() : null);
        List<Map<String, Object>> tableRows = data.rows().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Emp Code", r.empCode());
            row.put("Name", r.employeeName());
            row.put("Cadre", r.cadre());
            row.put("Designation", r.designation());
            row.put("Direction", r.deputationDirection());
            row.put("Organization", r.organizationName());
            row.put("Org Type", r.organizationType());
            row.put("Station", r.postingStation());
            row.put("Period From", r.periodFrom());
            row.put("Period To", r.periodTo());
            row.put("Pay Option", r.payOption());
            row.put("Dep. Allowance Rate %", r.deputationAllowanceRate());
            row.put("Dep. Allowance Cap", r.deputationAllowanceCap());
            row.put("LSPC Applicable", r.lspcApplicable());
            row.put("Status", r.status());
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Emp Code", "Name", "Cadre", "Designation", "Direction", "Organization", "Org Type", "Station",
                        "Period From", "Period To", "Pay Option", "Dep. Allowance Rate %", "Dep. Allowance Cap", "LSPC Applicable", "Status"),
                tableRows, tableRows.size());
    }
}
