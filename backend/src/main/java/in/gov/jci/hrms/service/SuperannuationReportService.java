package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.SuperannuationForecastEntry;
import in.gov.jci.hrms.dto.SuperannuationReportResponse;
import in.gov.jci.hrms.dto.SuperannuationWindowSummary;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/reports/pims/superannuation - PIMS_SPEC.md dashboard card 6.
 * Reads employee_superannuation_details.superannuation_date, which the DB
 * itself computes and maintains via fn_calculate_jci_superannuation_date
 * (58 years, last-day-of-month, for Regular cadre; 60 years / 5-year Board
 * Director tenure cap otherwise - see V31 migration and
 * sp_apply_director_ministry_extension). This service only forecasts
 * against that already-authoritative date, it doesn't recompute it.
 */
@Service
public class SuperannuationReportService implements PimsReportExportSource {

    private static final int[] STANDARD_WINDOWS_MONTHS = {6, 12, 24, 36, 60};
    private static final int DEFAULT_WINDOW_MONTHS = 60;

    private final JdbcTemplate jdbcTemplate;

    public SuperannuationReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SuperannuationReportResponse generate(PimsReportFilter filter) {
        List<SuperannuationWindowSummary> windowSummary = Arrays.stream(STANDARD_WINDOWS_MONTHS)
                .mapToObj(months -> new SuperannuationWindowSummary(months, countWithinWindow(months, filter)))
                .toList();

        int months = filter.months() != null ? filter.months() : DEFAULT_WINDOW_MONTHS;
        List<SuperannuationForecastEntry> entries = entriesWithinWindow(months, filter);
        return new SuperannuationReportResponse(windowSummary, entries);
    }

    private long countWithinWindow(int months, PimsReportFilter filter) {
        // CAST(? AS BIGINT) rather than a bare "? IS NULL": with the default,
        // no-filter call (departmentId null), PostgreSQL can't infer a type for
        // a parameter whose only use is "$1 IS NULL" with no typed sibling in
        // that expression, and fails with "could not determine data type of
        // parameter" before any row is read (see CadreStrengthReportService).
        String sql = "SELECT COUNT(*) FROM employee_superannuation_details sup "
                + "JOIN employees e ON e.id = sup.employee_id AND e.deleted_at IS NULL "
                + "WHERE sup.superannuation_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + (? || ' months')::INTERVAL) "
                + "AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT))";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, months, filter.departmentId(), filter.departmentId());
        return count != null ? count : 0;
    }

    private List<SuperannuationForecastEntry> entriesWithinWindow(int months, PimsReportFilter filter) {
        String sql = "SELECT e.id, e.employee_code, e.full_name, dept.name AS dept_name, des.title AS des_title, "
                + "e.date_of_birth, sup.superannuation_date, sup.is_board_director, sup.calculation_basis "
                + "FROM employee_superannuation_details sup "
                + "JOIN employees e ON e.id = sup.employee_id AND e.deleted_at IS NULL "
                + "LEFT JOIN departments dept ON dept.id = e.department_id "
                + "LEFT JOIN designations des ON des.id = e.designation_id "
                + "WHERE sup.superannuation_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + (? || ' months')::INTERVAL) "
                + "AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT)) "
                + "ORDER BY sup.superannuation_date";
        return jdbcTemplate.query(sql, (rs, i) -> {
            LocalDate superDate = toLocalDate(rs.getDate("superannuation_date"));
            return new SuperannuationForecastEntry(
                    rs.getLong("id"), rs.getString("employee_code"), rs.getString("full_name"),
                    rs.getString("dept_name"), rs.getString("des_title"), toLocalDate(rs.getDate("date_of_birth")),
                    superDate, rs.getBoolean("is_board_director"), rs.getString("calculation_basis"),
                    ChronoUnit.MONTHS.between(LocalDate.now(), superDate));
        }, months, filter.departmentId(), filter.departmentId());
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.SUPERANNUATION;
    }

    @Override
    public String exportTitle() {
        return "Superannuation Forecast";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<SuperannuationForecastEntry> entries = generate(filter).entries();
        List<Map<String, Object>> rows = entries.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", e.employeeCode());
            row.put("Name", e.employeeName());
            row.put("Department", e.departmentName());
            row.put("Designation", e.designationTitle());
            row.put("DOB", e.dateOfBirth());
            row.put("Superannuation Date", e.superannuationDate());
            row.put("Board Director", e.isBoardDirector() ? "Yes" : "No");
            row.put("Months Remaining", e.monthsRemaining());
            return row;
        }).toList();
        return new TabularReportResponse(
                List.of("Employee Code", "Name", "Department", "Designation", "DOB", "Superannuation Date", "Board Director", "Months Remaining"),
                rows, rows.size());
    }
}
