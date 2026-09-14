package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.CpfTrustPolicyProperties;
import in.gov.jci.hrms.dto.CpfSettlementEvaluation;
import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import in.gov.jci.hrms.dto.CpfTrustMemberSummaryResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/payroll/trust/members(/summary) - the CPF Trust Members' List, keyed primarily by CPF A/C No
 * and UAN rather than the general employee directory's name/code framing. CPF Trust membership runs from
 * onboarding through to actual fund settlement, which this codebase's own operating experience shows
 * routinely trails an employee's separation date by weeks or months - the settlement status/due-date/lag
 * columns below (via CpfSettlementCalculator) exist to surface exactly that backlog, not to duplicate the
 * general employee directory.
 *
 * Separation date is resolved as COALESCE(exit clearance release date, superannuation date), gated by the
 * employee's status actually being a separated one - employee_superannuation_details.superannuation_date is
 * populated for every employee as a future retirement-planning figure, not only ones who have actually left,
 * so the COALESCE fallback would otherwise show a bogus "separation date" decades out for active staff.
 *
 * CPF balance (EE/VPF/ER) comes from the member's most recent cpf_trust_member_ledger_entries row (a LATERAL
 * join, cheap - indexed on employee_id/value_date). Accrued interest (this FY's not-yet-posted shadow
 * accrual) is deliberately NOT computed in this same SQL pass for every member - CpfTrustPassbookService's
 * projection does bounded per-employee ledger lookups, so it's only evaluated for the current page's rows
 * after pagination, never against the full membership (see toResponse()).
 */
@Service
public class CpfTrustMemberDirectoryService {

    /** SQL fragment for a member's separation date - kept as one constant since it's needed in SELECT, the settlement-bucket CASE, and the separation-period filter alike (a WHERE clause can't reference a SELECT alias in the same query). */
    private static final String SEPARATION_DATE_EXPR =
            "CASE WHEN e.status IN ('RETIRED', 'RESIGNED', 'DECEASED', 'TERMINATED') "
                    + "THEN COALESCE(ecr.release_order_date, sup.superannuation_date) END";

    /** NOT_APPLICABLE / SETTLED / OVERDUE / IN_PROCESS / PENDING - mirrors CpfSettlementCalculator.calculateSettlementStatus() in SQL so it can be filtered/aggregated without pulling every row into Java first. Due-days is bound as a parameter, never string-concatenated, so CpfTrustPolicyProperties stays the single source of truth. */
    private static final String SETTLEMENT_BUCKET_EXPR =
            "CASE "
                    + "  WHEN " + SEPARATION_DATE_EXPR + " IS NULL THEN 'NOT_APPLICABLE' "
                    + "  WHEN ts.status = 'DISBURSED' THEN 'SETTLED' "
                    + "  WHEN CURRENT_DATE > (" + SEPARATION_DATE_EXPR + " + (? * INTERVAL '1 day')) THEN 'OVERDUE' "
                    + "  WHEN ts.status IS NOT NULL THEN 'IN_PROCESS' "
                    + "  ELSE 'PENDING' "
                    + "END";

    private static final String BASE_FROM =
            "FROM employees e "
                    + "LEFT JOIN LATERAL (SELECT x.release_order_date, x.initiated_date FROM exit_clearance_requests x "
                    + "  WHERE x.employee_id = e.id ORDER BY x.initiated_date DESC LIMIT 1) ecr ON true "
                    + "LEFT JOIN LATERAL (SELECT s.superannuation_date FROM employee_superannuation_details s "
                    + "  WHERE s.employee_id = e.id LIMIT 1) sup ON true "
                    + "LEFT JOIN LATERAL (SELECT t.status, t.updated_at FROM terminal_settlements t "
                    + "  WHERE t.employee_id = e.id ORDER BY t.created_at DESC LIMIT 1) ts ON true "
                    + "LEFT JOIN LATERAL (SELECT l.running_ee_balance, l.running_er_balance, l.running_vpf_balance, l.running_total_balance "
                    + "  FROM cpf_trust_member_ledger_entries l WHERE l.employee_id = e.id ORDER BY l.value_date DESC, l.id DESC LIMIT 1) ledger ON true ";

    private static final String SELECT_COLUMNS =
            "SELECT e.id, e.employee_code, e.full_name, e.cpf_ac_no, e.uan_no, e.status, "
                    + SEPARATION_DATE_EXPR + " AS separation_date, "
                    + "  ts.status AS raw_settlement_status, "
                    + "  CASE WHEN ts.status = 'DISBURSED' THEN ts.updated_at END AS settlement_at, "
                    + "  ledger.running_ee_balance, ledger.running_er_balance, ledger.running_vpf_balance ";

    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "cpfAcNo", "e.cpf_ac_no",
            "fullName", "e.full_name",
            "status", "e.status",
            "separationDate", SEPARATION_DATE_EXPR,
            "cpfBalance", "COALESCE(ledger.running_total_balance, 0)"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CpfTrustPolicyProperties policyProperties;
    private final CpfSettlementCalculator settlementCalculator;
    private final CpfRateResolutionService rateResolutionService;
    private final CpfTrustPassbookService passbookService;
    private final EmployeeRepository employeeRepository;

    public CpfTrustMemberDirectoryService(JdbcTemplate jdbcTemplate, CpfTrustPolicyProperties policyProperties,
                                           CpfSettlementCalculator settlementCalculator, CpfRateResolutionService rateResolutionService,
                                           CpfTrustPassbookService passbookService, EmployeeRepository employeeRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.policyProperties = policyProperties;
        this.settlementCalculator = settlementCalculator;
        this.rateResolutionService = rateResolutionService;
        this.passbookService = passbookService;
        this.employeeRepository = employeeRepository;
    }

    /**
     * PUT /{employeeId}/uan - the lightweight, CPF-scoped UAN correction action (distinct from the full
     * multi-field EmployeeRequest update). Employee is already @EntityListeners(AuditableEntityListener.class),
     * so this change lands in the standard audit trail automatically.
     *
     * saveAndFlush (not save) matters here: getOne() reads back via raw JdbcTemplate on the same connection,
     * which only sees what Hibernate has actually written to the database, not what's still buffered in its
     * session-level write-behind cache.
     */
    @Transactional
    public CpfTrustMemberResponse updateUan(Long employeeId, String uanNo) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        employee.setUanNo(uanNo);
        employeeRepository.saveAndFlush(employee);
        return getOne(employeeId);
    }

    /**
     * @param status "ACTIVE" or "SEPARATED" (the spec's own two-way Status filter grouping - never a raw
     *               EmployeeStatus value), or null/blank for both. "ACTIVE" here means "not separated" -
     *               ON_PROBATION/ON_LEAVE/SUSPENDED/INACTIVE all count as active for this module's purposes,
     *               matching isSeparated/SEPARATION_DATE_EXPR exactly rather than a literal status match.
     */
    public Page<CpfTrustMemberResponse> list(String status, String search, String settlementStatus, boolean uanMissingOnly,
                                              LocalDate separationFrom, LocalDate separationTo, Pageable pageable) {
        String normalizedStatus = status != null && !status.isBlank() ? status.toUpperCase() : null;
        String searchPattern = search != null && !search.isBlank() ? "%" + search.trim().toLowerCase() + "%" : null;
        String normalizedSettlementStatus = settlementStatus != null && !settlementStatus.isBlank() ? settlementStatus.toUpperCase() : null;
        int dueDays = policyProperties.getSettlementDueDays();

        StringBuilder where = new StringBuilder(
                "WHERE e.deleted_at IS NULL "
                        + "  AND (CAST(? AS VARCHAR) IS NULL "
                        + "       OR (CAST(? AS VARCHAR) = 'ACTIVE' AND " + SEPARATION_DATE_EXPR + " IS NULL) "
                        + "       OR (CAST(? AS VARCHAR) = 'SEPARATED' AND " + SEPARATION_DATE_EXPR + " IS NOT NULL)) "
                        + "  AND (CAST(? AS VARCHAR) IS NULL OR LOWER(e.employee_code) LIKE ? OR LOWER(e.full_name) LIKE ? "
                        + "       OR LOWER(e.cpf_ac_no) LIKE ? OR LOWER(e.uan_no) LIKE ?) ");
        List<Object> args = new ArrayList<>();
        args.add(normalizedStatus);
        args.add(normalizedStatus);
        args.add(normalizedStatus);
        args.add(searchPattern);
        args.add(searchPattern);
        args.add(searchPattern);
        args.add(searchPattern);
        args.add(searchPattern);

        if (uanMissingOnly) {
            where.append(" AND e.uan_no IS NULL ");
        }
        if (separationFrom != null || separationTo != null) {
            where.append(" AND ").append(SEPARATION_DATE_EXPR).append(" BETWEEN ? AND ? ");
            args.add(Date.valueOf(separationFrom != null ? separationFrom : LocalDate.of(1900, 1, 1)));
            args.add(Date.valueOf(separationTo != null ? separationTo : LocalDate.of(2999, 12, 31)));
        }
        if (normalizedSettlementStatus != null) {
            where.append(" AND ").append(SETTLEMENT_BUCKET_EXPR).append(" = ? ");
            args.add(dueDays);
            args.add(normalizedSettlementStatus);
        }

        String countSql = "SELECT COUNT(*) " + BASE_FROM + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        String orderBy = resolveOrderBy(pageable.getSort());
        String dataSql = SELECT_COLUMNS + BASE_FROM + where + "ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());

        List<Map<String, Object>> rows = jdbcTemplate.query(dataSql, (ResultSet rs, int i) -> rowToMap(rs), dataArgs.toArray());

        String currentFinYear = currentFinancialYear();
        BigDecimal baseRate = resolveBaseRateOrNull(currentFinYear);

        List<CpfTrustMemberResponse> content = rows.stream()
                .map(row -> toResponse(row, currentFinYear, baseRate))
                .toList();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    /** Single-member re-fetch after an in-place update (e.g. UpdateUan) - same enriched shape as a list row, without the caller having to search by a re-typed field to find it again. */
    public CpfTrustMemberResponse getOne(Long employeeId) {
        String sql = SELECT_COLUMNS + BASE_FROM + "WHERE e.deleted_at IS NULL AND e.id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (ResultSet rs, int i) -> rowToMap(rs), employeeId);
        if (rows.isEmpty()) {
            throw new EmployeeNotFoundException(employeeId);
        }
        String currentFinYear = currentFinancialYear();
        BigDecimal baseRate = resolveBaseRateOrNull(currentFinYear);
        return toResponse(rows.get(0), currentFinYear, baseRate);
    }

    public CpfTrustMemberSummaryResponse summary() {
        int dueDays = policyProperties.getSettlementDueDays();
        String sql = "SELECT "
                + "  COUNT(*) AS total_members, "
                + "  COUNT(*) FILTER (WHERE " + SEPARATION_DATE_EXPR + " IS NULL) AS active_accounts, "
                + "  COUNT(*) FILTER (WHERE " + SEPARATION_DATE_EXPR + " IS NOT NULL) AS separated_members, "
                + "  COUNT(*) FILTER (WHERE " + SEPARATION_DATE_EXPR + " IS NOT NULL AND ts.status IS DISTINCT FROM 'DISBURSED' "
                + "      AND CURRENT_DATE <= (" + SEPARATION_DATE_EXPR + " + (? * INTERVAL '1 day'))) AS pending_settlement, "
                + "  COUNT(*) FILTER (WHERE " + SEPARATION_DATE_EXPR + " IS NOT NULL AND ts.status IS DISTINCT FROM 'DISBURSED' "
                + "      AND CURRENT_DATE > (" + SEPARATION_DATE_EXPR + " + (? * INTERVAL '1 day'))) AS overdue_settlement, "
                + "  COUNT(*) FILTER (WHERE e.uan_no IS NULL) AS uan_missing, "
                + "  COALESCE(SUM(ledger.running_total_balance), 0) AS total_cpf_balance "
                + BASE_FROM + "WHERE e.deleted_at IS NULL";

        return jdbcTemplate.queryForObject(sql, (rs, i) -> new CpfTrustMemberSummaryResponse(
                rs.getLong("total_members"), rs.getLong("active_accounts"), rs.getLong("separated_members"),
                rs.getLong("pending_settlement"), rs.getLong("overdue_settlement"), rs.getLong("uan_missing"),
                rs.getBigDecimal("total_cpf_balance")), dueDays, dueDays);
    }

    /** A plain mutable map, deliberately not Map.of/Map.ofEntries - several columns here (separation_date, settlement_at, the ledger balances for a member with no ledger activity yet) are routinely null, and those factory methods reject null values outright. */
    private Map<String, Object> rowToMap(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("employee_code", rs.getString("employee_code"));
        row.put("full_name", rs.getString("full_name"));
        row.put("cpf_ac_no", rs.getString("cpf_ac_no"));
        row.put("uan_no", rs.getString("uan_no"));
        row.put("status", rs.getString("status"));
        row.put("separation_date", rs.getDate("separation_date"));
        row.put("raw_settlement_status", rs.getString("raw_settlement_status"));
        row.put("settlement_at", rs.getTimestamp("settlement_at"));
        row.put("running_ee_balance", rs.getBigDecimal("running_ee_balance"));
        row.put("running_er_balance", rs.getBigDecimal("running_er_balance"));
        row.put("running_vpf_balance", rs.getBigDecimal("running_vpf_balance"));
        return row;
    }

    private CpfTrustMemberResponse toResponse(Map<String, Object> row, String currentFinYear, BigDecimal baseRate) {
        Long employeeId = (Long) row.get("id");
        LocalDate separationDate = toLocalDate((Date) row.get("separation_date"));
        String rawSettlementStatus = (String) row.get("raw_settlement_status");
        java.sql.Timestamp settlementAt = (java.sql.Timestamp) row.get("settlement_at");
        LocalDate settlementDate = settlementAt != null ? settlementAt.toLocalDateTime().toLocalDate() : null;
        boolean isDisbursed = "DISBURSED".equals(rawSettlementStatus);
        boolean terminalSettlementStarted = rawSettlementStatus != null;

        CpfSettlementEvaluation evaluation = settlementCalculator.calculateSettlementStatus(
                separationDate, isDisbursed, terminalSettlementStarted, LocalDate.now());

        BigDecimal ee = orZero((BigDecimal) row.get("running_ee_balance"));
        BigDecimal er = orZero((BigDecimal) row.get("running_er_balance"));
        BigDecimal vpf = orZero((BigDecimal) row.get("running_vpf_balance"));
        BigDecimal cpfBalance = ee.add(er).add(vpf);

        BigDecimal accruedInterest = BigDecimal.ZERO;
        if (baseRate != null) {
            try {
                accruedInterest = passbookService.currentFyAccruedInterest(employeeId, currentFinYear, baseRate);
            } catch (RuntimeException ignored) {
                // No posted ledger activity yet for this FY, or a resolution edge case - accrued interest simply shows as 0 rather than failing the whole list.
            }
        }
        BigDecimal totalPayable = cpfBalance.add(accruedInterest);

        String employeeCode = (String) row.get("employee_code");
        String fullName = (String) row.get("full_name");
        String cpfAcNo = (String) row.get("cpf_ac_no");
        String uanNo = (String) row.get("uan_no");
        String status = (String) row.get("status");

        return new CpfTrustMemberResponse(
                employeeId, employeeCode, fullName, cpfAcNo, uanNo, status,
                separationDate != null, separationDate,
                ee, vpf, er, cpfBalance, accruedInterest, totalPayable,
                rawSettlementStatus, evaluation.status(), evaluation.dueDate(), settlementDate,
                evaluation.lagLabel(), evaluation.lagDays());
    }

    /** Best-effort: returns null (rather than throwing) when no statutory rate is notified at all - accrued interest then just shows as 0 for every row instead of failing the entire members' list. */
    private BigDecimal resolveBaseRateOrNull(String finYear) {
        try {
            return rateResolutionService.resolveStatutoryRate(finYear).baseRate();
        } catch (BusinessRuleViolationException ex) {
            return null;
        }
    }

    private static String resolveOrderBy(Sort sort) {
        if (sort != null && sort.isSorted()) {
            for (Sort.Order order : sort) {
                String column = SORTABLE_COLUMNS.get(order.getProperty());
                if (column != null) {
                    return column + (order.isDescending() ? " DESC" : " ASC");
                }
            }
        }
        return SORTABLE_COLUMNS.get("cpfAcNo") + " ASC";
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    /** April-March FY label ("2026-2027"), matching the app-wide convention (see IncomingFundTransferService.financialYearFor / the frontend's currentFinancialYear()). */
    private static String currentFinancialYear() {
        LocalDate now = LocalDate.now();
        int startYear = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }
}
