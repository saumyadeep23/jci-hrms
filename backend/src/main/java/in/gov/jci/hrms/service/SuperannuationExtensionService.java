package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.MinistryExtensionRequest;
import in.gov.jci.hrms.dto.SuperannuationCalculationPreviewResponse;
import in.gov.jci.hrms.dto.SuperannuationExtensionResponse;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.util.SuperannuationCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PIMS_SPEC.md operational-features task, Section 4: Director Ministry
 * Extension + a 58-vs-60-year Superannuation Calculator. Reads/writes go
 * through JdbcTemplate rather than JPA - applyMinistryExtension() calls the
 * live sp_apply_director_ministry_extension DB procedure (V31 migration)
 * directly, and both methods need a read that reflects that procedure's
 * effect immediately, which a same-transaction JPA repository read could
 * serve stale from its first-level cache.
 */
@Service
public class SuperannuationExtensionService {

    private final JdbcTemplate jdbcTemplate;

    public SuperannuationExtensionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SuperannuationExtensionResponse applyMinistryExtension(Long employeeId, MinistryExtensionRequest request) {
        requireEmployeeExists(employeeId);
        jdbcTemplate.update("CALL sp_apply_director_ministry_extension(?, ?, ?)",
                employeeId, request.orderNumber(), request.orderDate());
        return readExtensionState(employeeId);
    }

    public SuperannuationCalculationPreviewResponse calculationPreview(Long employeeId) {
        requireEmployeeExists(employeeId);

        List<Map<String, Object>> employeeRows = jdbcTemplate.queryForList(
                "SELECT date_of_birth FROM employees WHERE id = ? AND deleted_at IS NULL", employeeId);
        if (employeeRows.isEmpty()) {
            throw new EmployeeNotFoundException(employeeId);
        }
        LocalDate dob = toLocalDate((Date) employeeRows.get(0).get("date_of_birth"));

        LocalDate regular58 = SuperannuationCalculator.lastDayRuleAnniversary(dob, 58);
        LocalDate director60 = SuperannuationCalculator.lastDayRuleAnniversary(dob, 60);

        List<Map<String, Object>> supRows = jdbcTemplate.queryForList(
                "SELECT superannuation_date, calculation_basis, is_board_director, director_appointment_date, "
                        + "is_ministry_extended, ministry_extended_upto "
                        + "FROM employee_superannuation_details WHERE employee_id = ?", employeeId);

        LocalDate directorAppointmentDate = null;
        LocalDate actualDate = null;
        String basis = null;
        boolean isDirector = false;
        boolean ministryExtended = false;
        LocalDate ministryExtendedUpto = null;
        if (!supRows.isEmpty()) {
            Map<String, Object> row = supRows.get(0);
            directorAppointmentDate = toLocalDate((Date) row.get("director_appointment_date"));
            actualDate = toLocalDate((Date) row.get("superannuation_date"));
            basis = (String) row.get("calculation_basis");
            isDirector = Boolean.TRUE.equals(row.get("is_board_director"));
            ministryExtended = Boolean.TRUE.equals(row.get("is_ministry_extended"));
            ministryExtendedUpto = toLocalDate((Date) row.get("ministry_extended_upto"));
        }

        LocalDate director5YearTermDate = directorAppointmentDate != null ? directorAppointmentDate.plusYears(5) : null;

        return new SuperannuationCalculationPreviewResponse(
                employeeId, dob, regular58, director60, director5YearTermDate,
                actualDate, basis, isDirector, ministryExtended, ministryExtendedUpto);
    }

    private SuperannuationExtensionResponse readExtensionState(Long employeeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT is_ministry_extended, ministry_extension_order_no, ministry_extension_order_date, "
                        + "ministry_extended_upto, superannuation_date, calculation_basis "
                        + "FROM employee_superannuation_details WHERE employee_id = ?", employeeId);
        if (rows.isEmpty()) {
            throw new EmployeeNotFoundException(employeeId);
        }
        Map<String, Object> row = rows.get(0);
        return new SuperannuationExtensionResponse(
                employeeId,
                Boolean.TRUE.equals(row.get("is_ministry_extended")),
                (String) row.get("ministry_extension_order_no"),
                toLocalDate((Date) row.get("ministry_extension_order_date")),
                toLocalDate((Date) row.get("ministry_extended_upto")),
                toLocalDate((Date) row.get("superannuation_date")),
                (String) row.get("calculation_basis"));
    }

    private void requireEmployeeExists(Long employeeId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees WHERE id = ? AND deleted_at IS NULL", Long.class, employeeId);
        if (count == null || count == 0) {
            throw new EmployeeNotFoundException(employeeId);
        }
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
}
