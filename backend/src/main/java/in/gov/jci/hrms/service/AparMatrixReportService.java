package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AparMatrixReportResponse;
import in.gov.jci.hrms.dto.AparMatrixRow;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/reports/pims/apar-matrix - PIMS_SPEC.md Feature 6's Dynamic
 * Authority Discovery, read from vw_apar_routing_matrix (V32 migration:
 * every currently-active post incumbency joined to whoever currently holds
 * that post's operational/administrative/accepting-authority reporting
 * posts). "Dual charge" here means any non-SUBSTANTIVE assignment
 * (ADDITIONAL_CHARGE/ACTING/LOOK_AFTER) held >= 90 days - the same
 * threshold fn_generate_apar_instances_for_cycle uses to decide whether it
 * needs its own separate APAR instance.
 */
@Service
public class AparMatrixReportService implements PimsReportExportSource {

    // CAST(? AS BIGINT) rather than a bare "? IS NULL": with the default,
    // no-filter call (departmentId null), PostgreSQL can't infer a type for a
    // parameter whose only use is "$1 IS NULL" with no typed sibling in that
    // expression, and fails with "could not determine data type of
    // parameter" before any row is read (see CadreStrengthReportService).
    private static final String SQL =
            "SELECT m.appraisee_emp_id, m.appraisee_emp_code, m.appraisee_name, m.appraisee_post_title, "
                    + "m.appraisee_assignment_type, m.assignment_start_date, "
                    + "m.reporting_officer_name, m.reviewing_officer_name, m.accepting_officer_name "
                    + "FROM vw_apar_routing_matrix m "
                    + "JOIN post_master p ON p.id = m.appraisee_post_id "
                    + "WHERE (CAST(? AS BIGINT) IS NULL OR p.department_id = CAST(? AS BIGINT)) "
                    + "ORDER BY m.appraisee_name";

    private final JdbcTemplate jdbcTemplate;

    public AparMatrixReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AparMatrixReportResponse generate(PimsReportFilter filter) {
        List<AparMatrixRow> rows = jdbcTemplate.query(SQL, (rs, i) -> {
            String assignmentType = rs.getString("appraisee_assignment_type");
            LocalDate startDate = toLocalDate(rs.getDate("assignment_start_date"));
            boolean dualCharge = !"SUBSTANTIVE".equals(assignmentType) && startDate != null
                    && ChronoUnit.DAYS.between(startDate, LocalDate.now()) >= 90;
            return new AparMatrixRow(
                    rs.getLong("appraisee_emp_id"), rs.getString("appraisee_emp_code"), rs.getString("appraisee_name"),
                    rs.getString("appraisee_post_title"), assignmentType, startDate,
                    rs.getString("reporting_officer_name"), rs.getString("reviewing_officer_name"), rs.getString("accepting_officer_name"),
                    dualCharge);
        }, filter.departmentId(), filter.departmentId());

        long dualChargeCount = rows.stream().filter(AparMatrixRow::dualChargeOver90Days).count();
        return new AparMatrixReportResponse(rows, dualChargeCount);
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.APAR_MATRIX;
    }

    @Override
    public String exportTitle() {
        return "APAR Routing Matrix";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<AparMatrixRow> rows = generate(filter).rows();
        List<Map<String, Object>> tableRows = rows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", r.appraiseeEmployeeCode());
            row.put("Name", r.appraiseeName());
            row.put("Post", r.postTitle());
            row.put("Assignment Type", r.assignmentType());
            row.put("Since", r.assignmentStartDate());
            row.put("Reporting Officer", r.reportingOfficerName());
            row.put("Reviewing Officer", r.reviewingOfficerName());
            row.put("Accepting Officer", r.acceptingOfficerName());
            row.put("Dual Charge >= 90d", r.dualChargeOver90Days() ? "Yes" : "No");
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Employee Code", "Name", "Post", "Assignment Type", "Since", "Reporting Officer", "Reviewing Officer", "Accepting Officer", "Dual Charge >= 90d"),
                tableRows, tableRows.size());
    }
}
