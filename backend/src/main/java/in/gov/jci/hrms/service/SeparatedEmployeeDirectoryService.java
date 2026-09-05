package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.SeparatedEmployeeResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/employees/separated - PIMS "Separated Staff" tab. Everyone
 * with a terminal EmployeeStatus (see EmployeeSpecification.
 * SEPARATED_STATUSES), enriched with their last known post details (the
 * employees table's own department/designation/ro FKs, which
 * EmployeeReleaseService never touches - only post_incumbency gets
 * vacated), their last pay fixation (which release closes out with
 * effective_to rather than deleting), and each employee's most recent
 * exit-clearance/terminal-settlement status if either exists yet.
 */
@Service
public class SeparatedEmployeeDirectoryService implements PimsReportExportSource {

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM employees e "
                    + "WHERE e.deleted_at IS NULL AND e.status IN ('RETIRED', 'RESIGNED', 'DECEASED', 'TERMINATED') "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR e.status = CAST(? AS VARCHAR)) "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR LOWER(e.employee_code) LIKE ? OR LOWER(e.full_name) LIKE ?)";

    private static final String DATA_SQL =
            "SELECT e.id, e.employee_code, e.cpf_ac_no, e.full_name, "
                    + "  ecr.separation_type, COALESCE(ecr.release_order_date, sup.superannuation_date) AS separation_date, "
                    + "  des.title AS last_designation, dept.name AS last_department, ro.ro_name AS last_ro, "
                    + "  rpf.basic_pay AS last_basic_pay, rpf.scale_code AS last_scale_grade, "
                    + "  sup.pension_settlement_status, ecr.status AS clearance_status, ts.status AS settlement_status "
                    + "FROM employees e "
                    + "LEFT JOIN departments dept ON dept.id = e.department_id "
                    + "LEFT JOIN designations des ON des.id = e.designation_id "
                    + "LEFT JOIN ro_master ro ON ro.id = e.ro_id "
                    + "LEFT JOIN employee_superannuation_details sup ON sup.employee_id = e.id "
                    + "LEFT JOIN LATERAL (SELECT r.basic_pay, r.scale_code FROM regular_pay_fixations r "
                    + "  WHERE r.employee_id = e.id ORDER BY r.effective_from DESC LIMIT 1) rpf ON true "
                    + "LEFT JOIN LATERAL (SELECT x.separation_type, x.status, x.release_order_date, x.initiated_date FROM exit_clearance_requests x "
                    + "  WHERE x.employee_id = e.id ORDER BY x.initiated_date DESC LIMIT 1) ecr ON true "
                    + "LEFT JOIN LATERAL (SELECT t.status FROM terminal_settlements t "
                    + "  WHERE t.employee_id = e.id ORDER BY t.created_at DESC LIMIT 1) ts ON true "
                    + "WHERE e.deleted_at IS NULL AND e.status IN ('RETIRED', 'RESIGNED', 'DECEASED', 'TERMINATED') "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR e.status = CAST(? AS VARCHAR)) "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR LOWER(e.employee_code) LIKE ? OR LOWER(e.full_name) LIKE ?) "
                    + "ORDER BY separation_date DESC NULLS LAST, e.employee_code "
                    + "LIMIT ? OFFSET ?";

    private final JdbcTemplate jdbcTemplate;

    public SeparatedEmployeeDirectoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<SeparatedEmployeeResponse> list(String status, String search, Pageable pageable) {
        String statusFilter = status != null && !status.isBlank() ? status.toUpperCase() : null;
        String searchPattern = search != null && !search.isBlank() ? "%" + search.trim().toLowerCase() + "%" : null;

        Long total = jdbcTemplate.queryForObject(COUNT_SQL, Long.class,
                statusFilter, statusFilter, searchPattern, searchPattern, searchPattern);

        List<SeparatedEmployeeResponse> rows = jdbcTemplate.query(DATA_SQL, (ResultSet rs, int i) -> new SeparatedEmployeeResponse(
                        rs.getLong("id"), rs.getString("employee_code"), rs.getString("cpf_ac_no"), rs.getString("full_name"),
                        rs.getString("separation_type"), toLocalDate(rs.getDate("separation_date")),
                        rs.getString("last_designation"), rs.getString("last_department"), rs.getString("last_ro"),
                        rs.getBigDecimal("last_basic_pay"), rs.getString("last_scale_grade"),
                        rs.getString("pension_settlement_status"), rs.getString("clearance_status"), rs.getString("settlement_status")),
                statusFilter, statusFilter, searchPattern, searchPattern, searchPattern, pageable.getPageSize(), pageable.getOffset());

        return new PageImpl<>(rows, pageable, total != null ? total : 0);
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.SEPARATED_STAFF;
    }

    @Override
    public String exportTitle() {
        return "Separated Staff Register";
    }

    /** Export ignores most PimsReportFilter fields (department/designation/etc. don't apply to this report) - only filter.search() is honored, matching the on-screen search box. Unpaginated: exports every matching row. */
    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<SeparatedEmployeeResponse> rows = list(null, filter != null ? filter.search() : null, PageRequest.of(0, 10_000)).getContent();
        List<Map<String, Object>> tableRows = rows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Emp Code", r.employeeCode());
            row.put("CPF A/C No", r.cpfAcNo());
            row.put("Full Name", r.fullName());
            row.put("Separation Reason", r.separationType());
            row.put("Release Date", r.separationDate());
            row.put("Last Designation", r.lastDesignation());
            row.put("Last Basic Pay", r.lastBasicPay());
            row.put("Clearance Status", r.clearanceStatus());
            row.put("Settlement Status", r.settlementStatus());
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Emp Code", "CPF A/C No", "Full Name", "Separation Reason", "Release Date", "Last Designation",
                        "Last Basic Pay", "Clearance Status", "Settlement Status"),
                tableRows, tableRows.size());
    }
}
