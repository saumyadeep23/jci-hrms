package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.Manpower4TierReportResponse;
import in.gov.jci.hrms.dto.ManpowerTierRow;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/reports/pims/manpower-4tier - PIMS_SPEC.md dashboard card 4.
 * Each tier is aggregated as a monthly-equivalent cost (CASUAL's per-day
 * wage rate is converted via a 26-day working month) but reported as separate rows,
 * not blended into one grand total, since REGULAR/CONTRACTUAL/OUTSOURCED
 * still represent genuinely different compensation structures - see
 * ManpowerTierRow.
 */
@Service
public class Manpower4TierReportService implements PimsReportExportSource {

    /** Casual daily wage -> monthly-equivalent cost, assuming a 26-day working month. */
    private static final int CASUAL_WORKING_DAYS_PER_MONTH = 26;

    // CAST(? AS BIGINT) rather than a bare "? IS NULL": with the default,
    // no-filter call (departmentId null), PostgreSQL can't infer a type for a
    // parameter whose only use is "$1 IS NULL" with no typed sibling in that
    // expression, and fails with "could not determine data type of
    // parameter" before any row is read (see CadreStrengthReportService).
    //
    // REGULAR's basic pay comes from regular_pay_fixations (the historized,
    // increment/promotion-aware ledger - V50), falling back to
    // employee_employment_categories.regular_basic_pay only when an employee
    // somehow has no current fixation row - NOT the other way around, since
    // cat.regular_basic_pay is a point-in-time snapshot from initial
    // appointment/onboarding that never gets updated by increments/promotions
    // (MovementOrderService keeps it in sync on promotion only, per V50's own
    // migration comment - not on annual increments processed via
    // IncrementProcessingService).
    //
    // e.status = 'ACTIVE': employee_employment_categories rows aren't
    // cleaned up on separation (is_active stays true, deleted_at stays
    // null - see EmployeeReleaseService, which never touches this table),
    // so without this an employee released via retirement/resignation/
    // death would keep inflating "active manpower" headcount and cost
    // indefinitely.
    private static final String SQL =
            "SELECT cat.employment_category, "
                    + "COUNT(*) AS headcount, "
                    + "SUM(COALESCE(rpf.basic_pay, cat.regular_basic_pay, 0) + COALESCE(cat.daily_wage_rate, 0) * " + CASUAL_WORKING_DAYS_PER_MONTH
                    + "    + COALESCE(cat.fixed_lump_sum_monthly, 0) + COALESCE(cat.monthly_ctc, 0)) AS total_cost, "
                    + "SUM(COALESCE(cat.billing_rate_monthly, 0)) AS total_billing "
                    + "FROM employee_employment_categories cat "
                    + "JOIN employees e ON e.id = cat.employee_id AND e.deleted_at IS NULL AND e.status = 'ACTIVE' "
                    + "LEFT JOIN regular_pay_fixations rpf ON rpf.employee_id = e.id AND rpf.is_current = true "
                    + "WHERE cat.deleted_at IS NULL AND cat.is_active = true "
                    + "  AND (CAST(? AS BIGINT) IS NULL OR e.department_id = CAST(? AS BIGINT)) "
                    + "GROUP BY cat.employment_category ORDER BY cat.employment_category";

    private static final Map<String, String> COST_BASIS = Map.of(
            "REGULAR", "Basic Pay (monthly)",
            "CASUAL", "Wage Rate x 26 days (monthly)",
            "CONTRACTUAL", "Lump Sum (monthly)",
            "OUTSOURCED", "CTC (monthly)");

    private final JdbcTemplate jdbcTemplate;

    public Manpower4TierReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Manpower4TierReportResponse generate(PimsReportFilter filter) {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(SQL, filter.departmentId(), filter.departmentId());

        List<ManpowerTierRow> tiers = raw.stream().map(r -> {
            String category = (String) r.get("employment_category");
            long headcount = (Long) r.get("headcount");
            BigDecimal totalCost = (BigDecimal) r.get("total_cost");
            BigDecimal avgCost = headcount == 0 ? BigDecimal.ZERO : totalCost.divide(BigDecimal.valueOf(headcount), 2, RoundingMode.HALF_UP);
            return new ManpowerTierRow(category, COST_BASIS.getOrDefault(category, "N/A"), headcount, totalCost, avgCost);
        }).toList();

        long totalHeadcount = tiers.stream().mapToLong(ManpowerTierRow::headcount).sum();
        BigDecimal totalBilling = raw.stream()
                .map(r -> (BigDecimal) r.get("total_billing"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Manpower4TierReportResponse(tiers, totalHeadcount, totalBilling);
    }

    @Override
    public PimsReportType reportType() {
        return PimsReportType.MANPOWER_4TIER;
    }

    @Override
    public String exportTitle() {
        return "4-Tier Manpower Report";
    }

    @Override
    public TabularReportResponse tabularData(PimsReportFilter filter) {
        List<ManpowerTierRow> tiers = generate(filter).tiers();
        List<Map<String, Object>> rows = tiers.stream().map(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Tier", t.employmentCategory());
            row.put("Cost Basis", t.costBasis());
            row.put("Headcount", t.headcount());
            row.put("Total Cost", t.totalCost());
            row.put("Average Cost", t.averageCost());
            return row;
        }).toList();
        return new TabularReportResponse(List.of("Tier", "Cost Basis", "Headcount", "Total Cost", "Average Cost"), rows, rows.size());
    }
}
