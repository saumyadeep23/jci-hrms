package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AdHocReportRequest;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * POST /api/v1/reports/pims/ad-hoc - PIMS_SPEC.md dashboard card 4's
 * dynamic report builder over vw_jci_employee_master_360 (V32 migration).
 * Requested column names are validated against ALLOWED_COLUMNS before ever
 * being concatenated into SQL - never accept a raw client-supplied
 * identifier directly, only ones already known-safe (same pattern as
 * MasterDependencyService's hardcoded probe tables).
 */
@Service
public class AdHocReportService {

    /** Every column vw_jci_employee_master_360 exposes - see V32/V57 migrations. */
    public static final Set<String> ALLOWED_COLUMNS = Set.of(
            "employee_id", "cpf_ac_no", "employee_code", "full_name", "salutation", "date_of_birth", "age", "gender",
            "marital_status", "blood_group", "pan_number", "aadhaar_ref_number", "personal_email", "official_email",
            "personal_mobile", "official_mobile", "employment_status", "date_of_joining", "employment_category",
            "compensation_tier_summary", "current_basic_pay", "entry_basic_pay", "current_scale_code", "increment_cycle",
            "current_pay_effective_date", "daily_wage_rate", "fixed_lump_sum_monthly", "monthly_ctc",
            "outsourced_vendor_name", "current_post_id", "current_post_code", "current_post_title", "current_assignment_type",
            "post_assignment_start_date", "department_code", "department_name", "designation_code", "designation_title",
            "scale_grade", "ro_code", "ro_name", "dpc_code", "dpc_name", "active_bank_name", "active_bank_branch",
            "active_bank_account_no", "active_bank_ifsc", "bank_verification_status", "social_category", "is_pwbd",
            "disability_type", "superannuation_date", "is_board_director", "superannuation_calculation_basis",
            "pension_settlement_status");

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public AdHocReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TabularReportResponse generate(AdHocReportRequest request) {
        LinkedHashSet<String> columns = new LinkedHashSet<>(request.columns());
        for (String column : columns) {
            if (!ALLOWED_COLUMNS.contains(column)) {
                throw new BusinessRuleViolationException("Unknown or disallowed column: " + column);
            }
        }
        if (columns.isEmpty()) {
            throw new BusinessRuleViolationException("At least one column must be selected");
        }

        PimsReportFilter filter = request.filter() != null ? request.filter() : PimsReportFilter.empty();
        int page = request.page() != null ? Math.max(0, request.page()) : 0;
        int size = request.size() != null ? Math.min(MAX_SIZE, Math.max(1, request.size())) : DEFAULT_SIZE;

        String columnList = String.join(", ", columns);
        // Each pair casts its bare "? IS NULL" placeholder to the type its
        // filter value actually is (BIGINT for departmentId, VARCHAR for the
        // two String filters): with the default, no-filter call (every value
        // null), PostgreSQL can't infer a type for a parameter whose only use
        // is "$N IS NULL" with no typed sibling in that expression, and fails
        // the whole query with "could not determine data type of parameter"
        // before a single row is read (see CadreStrengthReportService, the
        // first fix for this bug class, for the full explanation).
        String whereClause = "WHERE (CAST(? AS BIGINT) IS NULL OR department_code = (SELECT code FROM departments WHERE id = ?)) "
                + "AND (CAST(? AS VARCHAR) IS NULL OR employment_category = ?) "
                + "AND (CAST(? AS VARCHAR) IS NULL OR social_category = ?)";
        String countSql = "SELECT COUNT(*) FROM vw_jci_employee_master_360 " + whereClause;
        String dataSql = "SELECT " + columnList + " FROM vw_jci_employee_master_360 " + whereClause + " ORDER BY employee_code LIMIT ? OFFSET ?";

        Object[] whereArgs = { filter.departmentId(), filter.departmentId(), filter.employmentCategory(), filter.employmentCategory(),
                filter.socialCategory(), filter.socialCategory() };

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, whereArgs);

        Object[] dataArgs = new Object[whereArgs.length + 2];
        System.arraycopy(whereArgs, 0, dataArgs, 0, whereArgs.length);
        dataArgs[whereArgs.length] = size;
        dataArgs[whereArgs.length + 1] = page * size;

        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(dataSql, dataArgs);
        List<Map<String, Object>> rows = rawRows.stream()
                .map(r -> {
                    Map<String, Object> ordered = new LinkedHashMap<>();
                    columns.forEach(c -> ordered.put(c, r.get(c)));
                    return ordered;
                })
                .collect(Collectors.toList());

        return new TabularReportResponse(List.copyOf(columns), rows, total != null ? total : 0);
    }
}
