package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.Circular53ComplianceRow;
import in.gov.jci.hrms.dto.CcsForm1Response;
import in.gov.jci.hrms.dto.EncashmentRegisterRow;
import in.gov.jci.hrms.dto.MusterRollRow;
import in.gov.jci.hrms.dto.PayrollCutoffFeedRow;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ALMS Enterprise Reporting and Analytics Workbench (Phase 4). Every report
 * is a JdbcTemplate raw-SQL aggregation over already-persisted data (see
 * CadreStrengthReportService for the established convention this mirrors),
 * not a re-run of AttendanceAggregationService's evaluation engine -
 * reporting reads history, it doesn't recompute it.
 *
 * Two structural gaps in the underlying schema, flagged rather than
 * papered over:
 *  - attendance_payroll_cutoff rows are never created by any service in this
 *    codebase (no freeze-trigger endpoint exists yet), so generatePayrollFeed
 *    computes its cycle window on the fly from the requested dates and only
 *    reports isLocked=true if a matching cutoff row happens to exist.
 *  - There is no encashment pay-rate rule anywhere in this codebase;
 *    EncashmentRegisterRow.estimatedAmount is a rough illustrative figure
 *    (daysClaimed * regularBasicPay / 30), not an authoritative computation.
 */
@Service
public class AlmsReportService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime GRACE_CUTOFF = LocalTime.of(10, 15);
    private static final LocalTime LATE_MAX_LIMIT = LocalTime.of(10, 45);
    private static final LocalTime EARLY_DEPARTURE_MIN = LocalTime.of(17, 15);
    private static final LocalTime STANDARD_END = LocalTime.of(18, 15);
    private static final Set<String> PRESENT_LIKE = Set.of("PRESENT", "GRACE_APPLIED", "LATE_SHORT_HOURS", "REQUIRES_REGULARIZATION", "UNAUTHORIZED_LATE");
    private static final Set<String> CONCESSION_STATUSES = Set.of("REQUIRES_REGULARIZATION", "UNAUTHORIZED_LATE");

    private final JdbcTemplate jdbcTemplate;

    public AlmsReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------------
    // 1. Statutory Muster Roll (Form II)
    // -------------------------------------------------------------------
    public Page<MusterRollRow> generateMusterRoll(LocalDate startDate, LocalDate endDate, Long officeId, String cadre, Pageable pageable) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleViolationException("endDate must not be before startDate");
        }
        String cadreFilter = (cadre == null || cadre.isBlank() || "ALL".equalsIgnoreCase(cadre)) ? null : cadre.toUpperCase();

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees e "
                        + "LEFT JOIN employee_employment_categories cat ON cat.employee_id = e.id AND cat.deleted_at IS NULL "
                        + "WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.ro_id = ? OR e.dpc_id = ?) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR cat.employment_category = ?)",
                Long.class, officeId, officeId, officeId, cadreFilter, cadreFilter);

        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
                "SELECT e.id, e.employee_code, e.first_name, e.last_name, d.title AS designation, "
                        + "COALESCE(dpc.dpc_name, ro.ro_name, 'Head Office') AS office_name, COALESCE(dpc.state, ro.state) AS state "
                        + "FROM employees e "
                        + "JOIN designations d ON d.id = e.designation_id "
                        + "LEFT JOIN ro_master ro ON ro.id = e.ro_id "
                        + "LEFT JOIN dpc_master dpc ON dpc.id = e.dpc_id "
                        + "LEFT JOIN employee_employment_categories cat ON cat.employee_id = e.id AND cat.deleted_at IS NULL "
                        + "WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.ro_id = ? OR e.dpc_id = ?) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR cat.employment_category = ?) "
                        + "ORDER BY e.employee_code LIMIT ? OFFSET ?",
                officeId, officeId, officeId, cadreFilter, cadreFilter, pageable.getPageSize(), pageable.getOffset());

        if (employees.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        List<Long> employeeIds = employees.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        String idList = employeeIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        Map<Long, Map<LocalDate, String[]>> attendanceByEmployee = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT da.employee_id, da.attendance_date, da.detail_status, lt.code AS leave_type_code "
                        + "FROM daily_attendance da "
                        + "LEFT JOIN leave_applications la ON la.id = da.leave_application_id "
                        + "LEFT JOIN leave_types lt ON lt.id = la.leave_type_id "
                        + "WHERE da.employee_id IN (" + idList + ") AND da.attendance_date BETWEEN ? AND ?",
                (rs, i) -> {
                    Long empId = rs.getLong("employee_id");
                    attendanceByEmployee
                            .computeIfAbsent(empId, k -> new LinkedHashMap<>())
                            .put(rs.getDate("attendance_date").toLocalDate(), new String[]{rs.getString("detail_status"), rs.getString("leave_type_code")});
                    return null;
                }, startDate, endDate);

        Map<Long, Set<LocalDate>> lwpDatesByEmployee = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT lle.employee_id, lle.entry_date FROM leave_ledger_entries lle "
                        + "JOIN leave_types lt ON lt.id = lle.leave_type_id "
                        + "WHERE lle.employee_id IN (" + idList + ") AND lt.code = 'LWP' AND lle.entry_date BETWEEN ? AND ?",
                (rs, i) -> {
                    lwpDatesByEmployee.computeIfAbsent(rs.getLong("employee_id"), k -> new java.util.HashSet<>()).add(rs.getDate("entry_date").toLocalDate());
                    return null;
                }, startDate, endDate);

        Map<Long, BigDecimal> penaltyDaysByEmployee = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT employee_id, SUM(ABS(delta_days)) AS total FROM leave_ledger_entries "
                        + "WHERE employee_id IN (" + idList + ") AND source = 'AUTO_LATE_DEDUCTION' AND entry_date BETWEEN ? AND ? "
                        + "GROUP BY employee_id",
                (rs, i) -> {
                    penaltyDaysByEmployee.put(rs.getLong("employee_id"), rs.getBigDecimal("total"));
                    return null;
                }, startDate, endDate);

        int totalCycleDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<MusterRollRow> rows = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            Long empId = ((Number) emp.get("id")).longValue();
            Map<LocalDate, String[]> attendance = attendanceByEmployee.getOrDefault(empId, Map.of());
            Set<LocalDate> lwpDates = lwpDatesByEmployee.getOrDefault(empId, Set.of());

            Map<String, String> dailyPunches = new LinkedHashMap<>();
            int present = 0, paidLeave = 0, weeklyOffHoliday = 0, lwp = 0, absent = 0;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String[] detail = attendance.get(date);
                String code = toMusterCode(detail != null ? detail[0] : null, detail != null ? detail[1] : null, lwpDates.contains(date));
                dailyPunches.put(String.valueOf(date.getDayOfMonth()), code);
                switch (code) {
                    case "P", "TR", "HD-CL" -> present++;
                    case "WO", "GH" -> weeklyOffHoliday++;
                    case "LWP" -> lwp++;
                    case "ABS" -> absent++;
                    default -> paidLeave++; // CL, RH, EL, HPL, COMM, or an unlisted leave-type code
                }
            }
            BigDecimal penaltyDays = penaltyDaysByEmployee.getOrDefault(empId, BigDecimal.ZERO);
            int penaltyDeductionDays = penaltyDays.setScale(0, RoundingMode.CEILING).intValue();
            int netPayableDays = totalCycleDays - lwp - absent - penaltyDeductionDays;

            rows.add(new MusterRollRow(
                    empId, (String) emp.get("employee_code"),
                    ((String) emp.get("first_name")) + " " + ((String) emp.get("last_name")),
                    (String) emp.get("designation"), (String) emp.get("office_name"), (String) emp.get("state"),
                    dailyPunches, totalCycleDays, present, paidLeave, weeklyOffHoliday, lwp, penaltyDeductionDays, netPayableDays));
        }
        return new PageImpl<>(rows, pageable, total);
    }

    /**
     * detail_status -> statutory muster code. LWP takes precedence over
     * everything (a ledger-confirmed LWP debit for the date overrides
     * whatever detail_status the day carries, since LWP is decided
     * downstream of the day's own evaluation - see
     * AttendanceLeaveDeductionService). Half-day sessions are CL-only
     * (LeaveValidationService), so HALF_DAY_PRESENT/HALF_DAY_SHORT always
     * map to HD-CL; HALF_DAY_ABSENT counts as a miss (ABS).
     */
    private static String toMusterCode(String detailStatus, String leaveTypeCode, boolean isLwpLedgerDay) {
        if (isLwpLedgerDay) return "LWP";
        if (detailStatus == null) return "ABS";
        return switch (detailStatus) {
            case "ON_TOUR" -> "TR";
            case "HOLIDAY" -> "GH";
            case "WEEKOFF" -> "WO";
            case "HALF_DAY_PRESENT", "HALF_DAY_SHORT" -> "HD-CL";
            case "HALF_DAY_ABSENT", "ABSENT" -> "ABS";
            case "ON_LEAVE" -> switch (leaveTypeCode == null ? "" : leaveTypeCode) {
                case "COMMUTED" -> "COMM";
                case "CL", "RH", "EL", "HPL" -> leaveTypeCode;
                default -> leaveTypeCode != null ? leaveTypeCode : "P";
            };
            default -> PRESENT_LIKE.contains(detailStatus) ? "P" : "ABS";
        };
    }

    // -------------------------------------------------------------------
    // 2. 25th Payroll Cutoff Disbursal Feed
    // -------------------------------------------------------------------
    public List<PayrollCutoffFeedRow> generatePayrollFeed(LocalDate periodStart, LocalDate periodEnd, Long officeId) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleViolationException("periodEnd must not be before periodStart");
        }
        int totalCycleDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        boolean locked = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM attendance_payroll_cutoff WHERE period_start = ? AND period_end = ? AND is_frozen = true)",
                Boolean.class, periodStart, periodEnd));

        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
                "SELECT e.id, e.employee_code, e.first_name, e.last_name FROM employees e "
                        + "WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.ro_id = ? OR e.dpc_id = ?) ORDER BY e.employee_code",
                officeId, officeId, officeId);
        if (employees.isEmpty()) return List.of();
        String idList = employees.stream().map(e -> String.valueOf(((Number) e.get("id")).longValue())).collect(Collectors.joining(","));

        Map<Long, Long> lwpDays = countByEmployee(
                "SELECT lle.employee_id, COUNT(DISTINCT lle.entry_date) AS c FROM leave_ledger_entries lle "
                        + "JOIN leave_types lt ON lt.id = lle.leave_type_id "
                        + "WHERE lle.employee_id IN (" + idList + ") AND lt.code = 'LWP' AND lle.entry_date BETWEEN ? AND ? GROUP BY lle.employee_id",
                periodStart, periodEnd);
        Map<Long, Long> absentDays = countByEmployee(
                "SELECT employee_id, COUNT(*) AS c FROM daily_attendance "
                        + "WHERE employee_id IN (" + idList + ") AND attendance_date BETWEEN ? AND ? AND detail_status IN ('ABSENT', 'HALF_DAY_ABSENT') "
                        + "GROUP BY employee_id",
                periodStart, periodEnd);
        Map<Long, BigDecimal> penaltyDays = sumByEmployee(
                "SELECT employee_id, SUM(ABS(delta_days)) AS s FROM leave_ledger_entries "
                        + "WHERE employee_id IN (" + idList + ") AND source = 'AUTO_LATE_DEDUCTION' AND entry_date BETWEEN ? AND ? GROUP BY employee_id",
                periodStart, periodEnd);
        Map<Long, BigDecimal> approvedElDays = sumByEmployee(
                "SELECT employee_id, SUM(el_days_claimed) AS s FROM leave_encashment_application "
                        + "WHERE employee_id IN (" + idList + ") AND encashment_type = 'IN_SERVICE_EL' "
                        + "AND hr_approval_status = 'APPROVED' AND finance_approval_status = 'APPROVED' "
                        + "AND created_at BETWEEN ? AND ? GROUP BY employee_id",
                toTimestamp(periodStart.atStartOfDay(IST).toInstant()), toTimestamp(periodEnd.plusDays(1).atStartOfDay(IST).toInstant()));

        List<PayrollCutoffFeedRow> rows = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            Long id = ((Number) emp.get("id")).longValue();
            BigDecimal lwp = BigDecimal.valueOf(lwpDays.getOrDefault(id, 0L));
            int absent = absentDays.getOrDefault(id, 0L).intValue();
            BigDecimal penalty = penaltyDays.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal payable = BigDecimal.valueOf(totalCycleDays).subtract(lwp).subtract(BigDecimal.valueOf(absent)).subtract(penalty);
            rows.add(new PayrollCutoffFeedRow(id, (String) emp.get("employee_code"),
                    ((String) emp.get("first_name")) + " " + ((String) emp.get("last_name")),
                    periodStart, periodEnd, totalCycleDays, payable, lwp, absent, penalty,
                    approvedElDays.getOrDefault(id, BigDecimal.ZERO), null, locked));
        }
        return rows;
    }

    // -------------------------------------------------------------------
    // 3. Circular 53 Concession Compliance
    // -------------------------------------------------------------------
    public List<Circular53ComplianceRow> generateCircular53Report(YearMonth yearMonth, Long officeId) {
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
                "SELECT e.id, e.employee_code, e.first_name, e.last_name, COALESCE(dpc.dpc_name, ro.ro_name, 'Head Office') AS office_name "
                        + "FROM employees e LEFT JOIN ro_master ro ON ro.id = e.ro_id LEFT JOIN dpc_master dpc ON dpc.id = e.dpc_id "
                        + "WHERE e.deleted_at IS NULL AND (CAST(? AS BIGINT) IS NULL OR e.ro_id = ? OR e.dpc_id = ?) ORDER BY e.employee_code",
                officeId, officeId, officeId);
        if (employees.isEmpty()) return List.of();
        String idList = employees.stream().map(e -> String.valueOf(((Number) e.get("id")).longValue())).collect(Collectors.joining(","));

        Map<Long, Long> graceCounts = countByEmployee(
                "SELECT employee_id, COUNT(*) AS c FROM daily_attendance "
                        + "WHERE employee_id IN (" + idList + ") AND attendance_date BETWEEN ? AND ? AND detail_status = 'GRACE_APPLIED' GROUP BY employee_id",
                monthStart, monthEnd);
        Map<Long, BigDecimal> penaltyByEmployee = sumByEmployee(
                "SELECT employee_id, SUM(ABS(delta_days)) AS s FROM leave_ledger_entries "
                        + "WHERE employee_id IN (" + idList + ") AND source = 'AUTO_LATE_DEDUCTION' AND entry_date BETWEEN ? AND ? GROUP BY employee_id",
                monthStart, monthEnd);

        Map<Long, long[]> lateEarlyStrikeCounts = new LinkedHashMap<>(); // [lateCount, earlyCount, unauthorizedCount]
        jdbcTemplate.query(
                "SELECT employee_id, detail_status, in_time, out_time FROM daily_attendance "
                        + "WHERE employee_id IN (" + idList + ") AND attendance_date BETWEEN ? AND ? "
                        + "AND detail_status IN ('REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE')",
                (rs, i) -> {
                    Long empId = rs.getLong("employee_id");
                    long[] counts = lateEarlyStrikeCounts.computeIfAbsent(empId, k -> new long[3]);
                    if ("UNAUTHORIZED_LATE".equals(rs.getString("detail_status"))) counts[2]++;
                    Timestamp inTs = rs.getTimestamp("in_time");
                    Timestamp outTs = rs.getTimestamp("out_time");
                    boolean isLate = inTs != null && isWithin(inTs.toInstant(), GRACE_CUTOFF, LATE_MAX_LIMIT);
                    boolean isEarly = !isLate && outTs != null && isWithin(outTs.toInstant(), EARLY_DEPARTURE_MIN, STANDARD_END);
                    if (isLate) counts[0]++;
                    else if (isEarly) counts[1]++;
                    return null;
                }, monthStart, monthEnd);

        String monthLabel = yearMonth.toString();
        List<Circular53ComplianceRow> rows = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            Long id = ((Number) emp.get("id")).longValue();
            long[] strikes = lateEarlyStrikeCounts.getOrDefault(id, new long[3]);
            rows.add(new Circular53ComplianceRow(id, (String) emp.get("employee_code"),
                    ((String) emp.get("first_name")) + " " + ((String) emp.get("last_name")), (String) emp.get("office_name"),
                    monthLabel, graceCounts.getOrDefault(id, 0L), strikes[0], strikes[1], strikes[2],
                    penaltyByEmployee.getOrDefault(id, BigDecimal.ZERO)));
        }
        return rows;
    }

    private static boolean isWithin(Instant instant, LocalTime start, LocalTime end) {
        LocalTime time = instant.atZone(IST).toLocalTime();
        return time.isAfter(start) && !time.isAfter(end);
    }

    // -------------------------------------------------------------------
    // 4. CCS Form 1 Bifurcated Leave Register
    // -------------------------------------------------------------------
    public CcsForm1Response generateCcsForm1(Long employeeId, int year) {
        Map<String, Object> employee = jdbcTemplate.queryForMap(
                "SELECT e.employee_code, e.first_name, e.last_name, d.title AS designation FROM employees e "
                        + "JOIN designations d ON d.id = e.designation_id WHERE e.id = ? AND e.deleted_at IS NULL", employeeId);
        Long elTypeId = jdbcTemplate.queryForObject("SELECT id FROM leave_types WHERE code = 'EL'", Long.class);
        Integer elCapDays = jdbcTemplate.queryForObject("SELECT max_accumulation_days FROM leave_types WHERE code = 'EL'", Integer.class);

        Map<String, Object> entitlement = jdbcTemplate.queryForList(
                "SELECT opening_balance, encashable_opening, enjoyable_opening, encashable_credited, enjoyable_credited, "
                        + "encashable_current, enjoyable_current FROM leave_entitlement_balance "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND year = ?",
                employeeId, elTypeId, year).stream().findFirst().orElse(Map.of());

        BigDecimal openingEncashable = asDecimal(entitlement.get("encashable_opening"));
        BigDecimal openingEnjoyable = asDecimal(entitlement.get("enjoyable_opening"));
        BigDecimal advanceEncashable = asDecimal(entitlement.get("encashable_credited"));
        BigDecimal advanceEnjoyable = asDecimal(entitlement.get("enjoyable_credited"));
        BigDecimal closingEncashable = asDecimal(entitlement.get("encashable_current"));
        BigDecimal closingEnjoyable = asDecimal(entitlement.get("enjoyable_current"));

        BigDecimal eolDeduction = jdbcTemplate.queryForList(
                        "SELECT SUM(ABS(delta_days)) AS s FROM leave_ledger_entries "
                                + "WHERE employee_id = ? AND source = 'EL_EOL_LAPSE_DEDUCTION' AND entry_date BETWEEN ? AND ?",
                        employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().findFirst().map(m -> asDecimal(m.get("s"))).orElse(BigDecimal.ZERO);

        List<CcsForm1Response.CcsForm1Transaction> transactions = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT la.start_date, la.end_date, la.debited_enjoyable_days, la.debited_encashable_days, la.id "
                        + "FROM leave_applications la JOIN leave_types lt ON lt.id = la.leave_type_id "
                        + "WHERE la.employee_id = ? AND lt.code = 'EL' AND la.status = 'APPROVED' "
                        + "AND EXTRACT(YEAR FROM la.start_date) = ? AND la.deleted_at IS NULL",
                (rs, i) -> transactions.add(new CcsForm1Response.CcsForm1Transaction(
                        rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(), "PHYSICAL_EL",
                        asDecimal(rs.getBigDecimal("debited_enjoyable_days")), asDecimal(rs.getBigDecimal("debited_encashable_days")),
                        "LA-" + rs.getLong("id"))),
                employeeId, year);
        jdbcTemplate.query(
                "SELECT id, el_days_claimed, created_at FROM leave_encashment_application "
                        + "WHERE employee_id = ? AND hr_approval_status = 'APPROVED' AND finance_approval_status = 'APPROVED' "
                        + "AND EXTRACT(YEAR FROM created_at) = ?",
                (rs, i) -> {
                    LocalDate txnDate = rs.getTimestamp("created_at").toInstant().atZone(IST).toLocalDate();
                    transactions.add(new CcsForm1Response.CcsForm1Transaction(
                            txnDate, txnDate, "ENCASHMENT", BigDecimal.ZERO, rs.getBigDecimal("el_days_claimed"), "ENC-" + rs.getLong("id")));
                    return null;
                }, employeeId, year);
        transactions.sort((a, b) -> a.fromDate().compareTo(b.fromDate()));

        BigDecimal totalBalance = closingEncashable.add(closingEnjoyable);
        BigDecimal surplusBuffer = elCapDays != null
                ? closingEncashable.subtract(BigDecimal.valueOf(elCapDays)).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        return new CcsForm1Response(employeeId, (String) employee.get("employee_code"),
                ((String) employee.get("first_name")) + " " + ((String) employee.get("last_name")), (String) employee.get("designation"),
                year, openingEnjoyable, openingEncashable, advanceEnjoyable, advanceEncashable, eolDeduction, transactions,
                closingEnjoyable, closingEncashable, totalBalance, surplusBuffer);
    }

    // -------------------------------------------------------------------
    // 5. In-Service Encashment Register
    // -------------------------------------------------------------------
    public List<EncashmentRegisterRow> generateEncashmentRegister(LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT lea.id, e.employee_code, e.first_name, e.last_name, e.date_of_joining, lea.encashment_type, "
                        + "lea.el_days_claimed, hr.employee_code AS hr_approved_by, lea.hr_approved_at, "
                        + "fin.employee_code AS finance_approved_by, lea.finance_approved_at, lea.is_payroll_eligible, "
                        + "lea.service_book_entry_id, COALESCE(rpf.basic_pay, cat.regular_basic_pay) AS regular_basic_pay, lea.created_at "
                        + "FROM leave_encashment_application lea "
                        + "JOIN employees e ON e.id = lea.employee_id "
                        + "LEFT JOIN employees hr ON hr.id = lea.hr_approved_by "
                        + "LEFT JOIN employees fin ON fin.id = lea.finance_approved_by "
                        + "LEFT JOIN employee_employment_categories cat ON cat.employee_id = e.id AND cat.deleted_at IS NULL "
                        + "LEFT JOIN regular_pay_fixations rpf ON rpf.employee_id = e.id AND rpf.is_current = true "
                        + "WHERE lea.hr_approval_status = 'APPROVED' AND lea.finance_approval_status = 'APPROVED' "
                        + "AND lea.created_at BETWEEN ? AND ? ORDER BY lea.created_at DESC",
                toTimestamp(fromDate.atStartOfDay(IST).toInstant()), toTimestamp(toDate.plusDays(1).atStartOfDay(IST).toInstant()));

        List<EncashmentRegisterRow> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate joining = ((java.sql.Date) row.get("date_of_joining")).toLocalDate();
            Instant createdAt = ((Timestamp) row.get("created_at")).toInstant();
            Period tenure = Period.between(joining, createdAt.atZone(IST).toLocalDate());
            BigDecimal daysClaimed = asDecimal(row.get("el_days_claimed"));
            BigDecimal basicPay = row.get("regular_basic_pay") != null ? asDecimal(row.get("regular_basic_pay")) : null;
            BigDecimal estimatedAmount = basicPay != null
                    ? daysClaimed.multiply(basicPay).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP)
                    : null;

            result.add(new EncashmentRegisterRow(
                    ((Number) row.get("id")).longValue(), (String) row.get("employee_code"),
                    ((String) row.get("first_name")) + " " + ((String) row.get("last_name")),
                    (String) row.get("encashment_type"), tenure.getYears() + "y " + tenure.getMonths() + "m",
                    daysClaimed, (String) row.get("hr_approved_by"),
                    row.get("hr_approved_at") != null ? ((Timestamp) row.get("hr_approved_at")).toInstant() : null,
                    (String) row.get("finance_approved_by"),
                    row.get("finance_approved_at") != null ? ((Timestamp) row.get("finance_approved_at")).toInstant() : null,
                    Boolean.TRUE.equals(row.get("is_payroll_eligible")),
                    row.get("service_book_entry_id") != null ? ((Number) row.get("service_book_entry_id")).longValue() : null,
                    estimatedAmount));
        }
        return result;
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------
    private Map<Long, Long> countByEmployee(String sql, Object... args) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (rs, i) -> {
            result.put(rs.getLong("employee_id"), rs.getLong("c"));
            return null;
        }, args);
        return result;
    }

    private Map<Long, BigDecimal> sumByEmployee(String sql, Object... args) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (rs, i) -> {
            result.put(rs.getLong("employee_id"), rs.getBigDecimal(2));
            return null;
        }, args);
        return result;
    }

    /** Plain JdbcTemplate varargs binding has no static parameter-type metadata (unlike Hibernate's JPQL binding), so a raw java.time.Instant arg fails with "Can't infer the SQL type" - java.sql.Timestamp has a well-known JDBC mapping instead. */
    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
