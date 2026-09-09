package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/v1/payroll/trust/members - the CPF Trust Members' List, keyed primarily by CPF A/C No and UAN
 * rather than the general employee directory's name/code framing. Unlike SeparatedEmployeeDirectoryService
 * (whose PIMS report is scoped to separated staff only), this covers every employee - CPF Trust membership
 * runs from onboarding through to actual fund settlement, which this codebase's own operating experience
 * shows routinely trails an employee's separation date by weeks or months (pendingSettlement=true below
 * surfaces exactly that backlog: separated members whose CPF hasn't been disbursed yet).
 *
 * Separation date is resolved the same way SeparatedEmployeeDirectoryService's PIMS report does -
 * COALESCE(exit clearance release date, superannuation date) - since terminal_settlements itself may not
 * exist yet for a just-separated member (settlement usually starts, and finishes, after separation).
 * cpfSettlementDate is only populated once terminal_settlements.status reaches DISBURSED (its updated_at at
 * that point being the closest signal this schema has to an actual CPF disbursement date - there is no
 * dedicated disbursed_at column, and CpfLedgerEntryType.FINAL_SETTLEMENT is declared but never posted by
 * any service yet).
 */
@Service
public class CpfTrustMemberDirectoryService {

    private static final String BASE_FROM =
            "FROM employees e "
                    + "LEFT JOIN departments dept ON dept.id = e.department_id "
                    + "LEFT JOIN designations des ON des.id = e.designation_id "
                    + "LEFT JOIN LATERAL (SELECT x.release_order_date FROM exit_clearance_requests x "
                    + "  WHERE x.employee_id = e.id ORDER BY x.initiated_date DESC LIMIT 1) ecr ON true "
                    + "LEFT JOIN LATERAL (SELECT s.superannuation_date FROM employee_superannuation_details s "
                    + "  WHERE s.employee_id = e.id LIMIT 1) sup ON true "
                    + "LEFT JOIN LATERAL (SELECT t.status, t.updated_at FROM terminal_settlements t "
                    + "  WHERE t.employee_id = e.id ORDER BY t.created_at DESC LIMIT 1) ts ON true ";

    private static final String BASE_WHERE =
            "WHERE e.deleted_at IS NULL "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR e.status = CAST(? AS VARCHAR)) "
                    + "  AND (CAST(? AS VARCHAR) IS NULL OR LOWER(e.employee_code) LIKE ? OR LOWER(e.full_name) LIKE ? "
                    + "       OR LOWER(e.cpf_ac_no) LIKE ? OR LOWER(e.uan_no) LIKE ?) ";

    /** Only when pendingSettlementOnly is requested - separated members whose CPF isn't DISBURSED yet, the exact backlog this list exists to surface. */
    private static final String PENDING_SETTLEMENT_CLAUSE =
            "  AND e.status IN ('RETIRED', 'RESIGNED', 'DECEASED', 'TERMINATED') "
                    + "  AND (ts.status IS NULL OR ts.status <> 'DISBURSED') ";

    /**
     * employee_superannuation_details.superannuation_date is populated for every employee as a future
     * retirement-planning figure, not only for ones who have actually separated - so the COALESCE fallback
     * to it is only valid once e.status itself confirms the employee has actually left (same status list
     * SeparatedEmployeeDirectoryService's own WHERE clause restricts to). Without this guard, every still-
     * active employee would show a bogus "separation date" decades in the future.
     */
    private static final String SELECT_COLUMNS =
            "SELECT e.id, e.employee_code, e.full_name, e.cpf_ac_no, e.uan_no, e.status, "
                    + "  dept.name AS department_name, des.title AS designation_name, "
                    + "  CASE WHEN e.status IN ('RETIRED', 'RESIGNED', 'DECEASED', 'TERMINATED') "
                    + "       THEN COALESCE(ecr.release_order_date, sup.superannuation_date) END AS separation_date, "
                    + "  ts.status AS cpf_settlement_status, "
                    + "  CASE WHEN ts.status = 'DISBURSED' THEN ts.updated_at END AS cpf_settlement_at ";

    private final JdbcTemplate jdbcTemplate;

    public CpfTrustMemberDirectoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<CpfTrustMemberResponse> list(String status, String search, boolean pendingSettlementOnly, Pageable pageable) {
        String normalizedStatus = status != null && !status.isBlank() ? status.toUpperCase() : null;
        String searchPattern = search != null && !search.isBlank() ? "%" + search.trim().toLowerCase() + "%" : null;
        String extraClause = pendingSettlementOnly ? PENDING_SETTLEMENT_CLAUSE : "";

        List<Object> filterArgs = new ArrayList<>();
        filterArgs.add(normalizedStatus);
        filterArgs.add(normalizedStatus);
        filterArgs.add(searchPattern);
        filterArgs.add(searchPattern);
        filterArgs.add(searchPattern);
        filterArgs.add(searchPattern);
        filterArgs.add(searchPattern);

        String countSql = "SELECT COUNT(*) " + BASE_FROM + BASE_WHERE + extraClause;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, filterArgs.toArray());

        String dataSql = SELECT_COLUMNS + BASE_FROM + BASE_WHERE + extraClause + "ORDER BY e.employee_code LIMIT ? OFFSET ?";
        List<Object> dataArgs = new ArrayList<>(filterArgs);
        dataArgs.add(pageable.getPageSize());
        dataArgs.add(pageable.getOffset());

        List<CpfTrustMemberResponse> rows = jdbcTemplate.query(dataSql, (ResultSet rs, int i) -> {
            LocalDate separationDate = toLocalDate(rs.getDate("separation_date"));
            LocalDate settlementDate = toLocalDate(rs.getTimestamp("cpf_settlement_at"));
            Long lagDays = (separationDate != null && settlementDate != null)
                    ? ChronoUnit.DAYS.between(separationDate, settlementDate) : null;
            return new CpfTrustMemberResponse(
                    rs.getLong("id"), rs.getString("employee_code"), rs.getString("full_name"),
                    rs.getString("cpf_ac_no"), rs.getString("uan_no"), rs.getString("status"),
                    rs.getString("department_name"), rs.getString("designation_name"),
                    separationDate, rs.getString("cpf_settlement_status"), settlementDate, lagDays);
        }, dataArgs.toArray());

        return new PageImpl<>(rows, pageable, total != null ? total : 0);
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    private static LocalDate toLocalDate(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime().toLocalDate() : null;
    }
}
