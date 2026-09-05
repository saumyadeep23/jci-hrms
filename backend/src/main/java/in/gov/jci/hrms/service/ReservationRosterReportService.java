package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.ReservationRosterReportResponse;
import in.gov.jci.hrms.dto.ReservationRosterRow;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/reports/pims/reservation-roster - PIMS_SPEC.md dashboard card
 * 4's reservation register. This is a representation-percentage summary
 * against the current DoPT/DPE model-roster unreserved-vacancy targets
 * (SC 15%, ST 7.5%, OBC-NCL 27%, EWS 10%; PwBD 4% horizontal), split by
 * Direct Recruitment vs. Promotion stream. It is NOT the full statutory
 * 100-point vacancy-by-vacancy roster register (which tracks reservation
 * status per individual roster point/vacancy with carry-forward across
 * cycles) - that needs a dedicated point-tracking table this schema
 * doesn't have yet. Source data comes from employee_social_profiles, an
 * optional group in the onboarding wizard's Steps 1/7
 * (EmployeeOnboardingService.saveSocialProfile) - an employee onboarded
 * without it still defaults to social_category = 'GEN' via this query's
 * LEFT JOIN.
 */
@Service
public class ReservationRosterReportService implements PimsReportExportSource {

    private static final Map<String, BigDecimal> STATUTORY_TARGETS = Map.of(
            "SC", new BigDecimal("15.0"),
            "ST", new BigDecimal("7.5"),
            "OBC", new BigDecimal("27.0"),
            "EWS", new BigDecimal("10.0"));
    private static final BigDecimal PWBD_TARGET = new BigDecimal("4.0");

    private final JdbcTemplate jdbcTemplate;

    public ReservationRosterReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReservationRosterReportResponse generate(PimsReportFilter filter) {
        // CAST(? AS BIGINT) rather than a bare "? IS NULL" on every one of this
        // method's three department_id filters: with the default, no-filter
        // call (departmentId null), PostgreSQL can't infer a type for a
        // parameter whose only use is "$1 IS NULL" with no typed sibling in
        // that expression, and fails with "could not determine data type of
        // parameter" before any row is read (see CadreStrengthReportService).
        String sql = "SELECT "
                + "  CASE COALESCE(sp.social_category, 'GEN') WHEN 'GEN' THEN 'UR' WHEN 'OBC_NCL' THEN 'OBC' ELSE sp.social_category END AS category, "
                + "  CASE rd.recruitment_mode WHEN 'DIRECT_RECRUITMENT' THEN 'DIRECT_RECRUITMENT' WHEN 'PROMOTION' THEN 'PROMOTION' ELSE 'OTHER' END AS stream, "
                + "  COUNT(*) AS cnt "
                + "FROM employees e "
                + "LEFT JOIN employee_social_profiles sp ON sp.employee_id = e.id "
                + "LEFT JOIN employee_recruitment_details rd ON rd.employee_id = e.id "
                + "WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT)) "
                + "GROUP BY category, stream";

        List<Map<String, Object>> raw = jdbcTemplate.queryForList(sql, filter.departmentId(), filter.departmentId());

        Map<String, Long> streamTotals = new HashMap<>();
        for (Map<String, Object> r : raw) {
            String stream = (String) r.get("stream");
            streamTotals.merge(stream, (Long) r.get("cnt"), Long::sum);
        }

        List<ReservationRosterRow> rows = raw.stream()
                .map(r -> {
                    String category = (String) r.get("category");
                    String stream = (String) r.get("stream");
                    long count = (Long) r.get("cnt");
                    long total = streamTotals.getOrDefault(stream, 0L);
                    BigDecimal target = STATUTORY_TARGETS.get(category);
                    return ReservationRosterRow.of(category, stream, count, total, target);
                })
                .sorted((a, b) -> {
                    int byStream = a.recruitmentStream().compareTo(b.recruitmentStream());
                    return byStream != 0 ? byStream : a.socialCategory().compareTo(b.socialCategory());
                })
                .toList();

        Long totalEmployees = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees e WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT))",
                Long.class, filter.departmentId(), filter.departmentId());
        Long pwbdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees e JOIN employee_social_profiles sp ON sp.employee_id = e.id "
                        + "WHERE sp.is_pwbd = true AND e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT))",
                Long.class, filter.departmentId(), filter.departmentId());

        long total = totalEmployees != null ? totalEmployees : 0;
        long pwbd = pwbdCount != null ? pwbdCount : 0;
        BigDecimal pwbdPercentage = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(pwbd).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new ReservationRosterReportResponse(rows, pwbd, pwbdPercentage, total);
    }

    /** Exposed for the frontend's KPI ribbon to compare against the statutory targets without duplicating the constants. */
    public BigDecimal pwbdTarget() {
        return PWBD_TARGET;
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.RESERVATION_ROSTER;
    }

    @Override
    public String exportTitle() {
        return "Reservation Roster Summary";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        ReservationRosterReportResponse report = generate(filter);
        List<Map<String, Object>> rows = report.rows().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Category", r.socialCategory());
            row.put("Stream", r.recruitmentStream());
            row.put("Count", r.count());
            row.put("Representation %", r.representationPercentage());
            row.put("Statutory Target %", r.statutoryTargetPercentage() != null ? r.statutoryTargetPercentage() : "N/A");
            return row;
        }).toList();
        return new TabularReportResponse(List.of("Category", "Stream", "Count", "Representation %", "Statutory Target %"), rows, rows.size());
    }
}
