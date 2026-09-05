package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CadreStrengthReportResponse;
import in.gov.jci.hrms.dto.CadreStrengthRow;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/reports/pims/cadre-strength - PIMS_SPEC.md dashboard card 2's
 * report. Location classification mirrors PostMasterRepository.search's own
 * HEAD_OFFICE/REGIONAL_OFFICE/DPC JPQL logic, done here as one grouped SQL
 * query instead of paging through every post in Java.
 */
@Service
public class CadreStrengthReportService implements PimsReportExportSource {

    private static final String LOCATION_SQL =
            "SELECT "
                    + "  CASE WHEN p.dpc_id IS NOT NULL THEN 'DPC' "
                    + "       WHEN ro.office_type = 'HEAD_OFFICE' THEN 'HEAD_OFFICE' "
                    + "       WHEN p.ro_id IS NOT NULL THEN 'REGIONAL_OFFICE' "
                    + "       ELSE 'UNASSIGNED' END AS location_type, "
                    + "  COUNT(*) FILTER (WHERE p.vacancy_status <> 'ABOLISHED') AS sanctioned, "
                    + "  COUNT(*) FILTER (WHERE p.vacancy_status = 'OCCUPIED') AS occupied, "
                    + "  COUNT(*) FILTER (WHERE p.vacancy_status = 'VACANT') AS vacant, "
                    + "  COUNT(*) FILTER (WHERE p.vacancy_status = 'FROZEN') AS frozen "
                    + "FROM post_master p "
                    + "LEFT JOIN ro_master ro ON ro.id = p.ro_id "
                    + "WHERE p.deleted_at IS NULL "
                    // CAST(? AS BIGINT) rather than a bare "? IS NULL": when every filter
                    // is null (the report's default, no-filter landing state - the exact
                    // case UAT hit), PostgreSQL can't infer a type for a parameter whose
                    // only use is "$1 IS NULL" with no typed sibling in that expression,
                    // and fails the whole query with "could not determine data type of
                    // parameter $1" - a 500, before a single row is even read. The cast
                    // pins the type explicitly so this works whether the filter is null
                    // or set.
                    + "  AND (CAST(? AS BIGINT) IS NULL OR p.department_id = CAST(? AS BIGINT)) "
                    + "  AND (CAST(? AS BIGINT) IS NULL OR p.ro_id = CAST(? AS BIGINT)) "
                    + "  AND (CAST(? AS BIGINT) IS NULL OR p.dpc_id = CAST(? AS BIGINT)) "
                    + "GROUP BY location_type ORDER BY location_type";

    private final JdbcTemplate jdbcTemplate;

    public CadreStrengthReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CadreStrengthReportResponse generate(PimsReportFilter filter) {
        List<CadreStrengthRow> byLocation = jdbcTemplate.query(LOCATION_SQL,
                (rs, i) -> CadreStrengthRow.of(rs.getString("location_type"), rs.getLong("sanctioned"),
                        rs.getLong("occupied"), rs.getLong("vacant"), rs.getLong("frozen")),
                filter.departmentId(), filter.departmentId(), filter.roId(), filter.roId(), filter.dpcId(), filter.dpcId());

        long sanctioned = byLocation.stream().mapToLong(CadreStrengthRow::sanctioned).sum();
        long occupied = byLocation.stream().mapToLong(CadreStrengthRow::occupied).sum();
        long vacant = byLocation.stream().mapToLong(CadreStrengthRow::vacant).sum();
        long frozen = byLocation.stream().mapToLong(CadreStrengthRow::frozen).sum();
        CadreStrengthRow overall = CadreStrengthRow.of("ALL", sanctioned, occupied, vacant, frozen);

        return new CadreStrengthReportResponse(byLocation, overall);
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.CADRE_STRENGTH;
    }

    @Override
    public String exportTitle() {
        return "Cadre Strength Statement";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<CadreStrengthRow> rows = generate(filter).byLocation();
        List<Map<String, Object>> tableRows = rows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Location", r.locationType());
            row.put("Sanctioned", r.sanctioned());
            row.put("Occupied", r.occupied());
            row.put("Vacant", r.vacant());
            row.put("Frozen", r.frozen());
            row.put("Occupancy %", r.occupancyPercentage());
            return row;
        }).toList();
        return new TabularReportResponse(List.of("Location", "Sanctioned", "Occupied", "Vacant", "Frozen", "Occupancy %"), tableRows, tableRows.size());
    }
}
