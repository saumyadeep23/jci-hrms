package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.Employee360Response;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** GET /api/employees/:id/360 - PIMS_SPEC.md's Employee 360 profile drawer, read from vw_jci_employee_master_360. */
@Service
public class Employee360Service {

    private static final RowMapper<Employee360Response> MAPPER = Employee360Service::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public Employee360Service(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Employee360Response getById(Long employeeId) {
        List<Employee360Response> results = jdbcTemplate.query(
                "SELECT * FROM vw_jci_employee_master_360 WHERE employee_id = ?", MAPPER, employeeId);
        if (results.isEmpty()) {
            throw new MasterDataNotFoundException("Employee", employeeId);
        }
        return results.get(0);
    }

    private static Employee360Response mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Employee360Response(
                rs.getLong("employee_id"), rs.getString("cpf_ac_no"), rs.getString("employee_code"), rs.getString("full_name"),
                rs.getString("salutation"), toLocalDate(rs.getDate("date_of_birth")), (Integer) rs.getObject("age"),
                rs.getString("gender"), rs.getString("marital_status"), rs.getString("blood_group"), rs.getString("pan_number"),
                rs.getString("aadhaar_ref_number"), rs.getString("personal_email"), rs.getString("official_email"),
                rs.getString("personal_mobile"), rs.getString("official_mobile"), rs.getString("employment_status"),
                toLocalDate(rs.getDate("date_of_joining")), rs.getString("employment_category"), rs.getString("compensation_tier_summary"),
                rs.getBigDecimal("current_basic_pay"), rs.getBigDecimal("entry_basic_pay"), rs.getString("current_scale_code"),
                rs.getString("increment_cycle"), toLocalDate(rs.getDate("current_pay_effective_date")),
                rs.getBigDecimal("daily_wage_rate"), rs.getBigDecimal("fixed_lump_sum_monthly"),
                rs.getBigDecimal("monthly_ctc"), rs.getString("outsourced_vendor_name"),
                (Long) rs.getObject("current_post_id"), rs.getString("current_post_code"), rs.getString("current_post_title"),
                rs.getString("current_assignment_type"), toLocalDate(rs.getDate("post_assignment_start_date")),
                rs.getString("department_code"), rs.getString("department_name"), rs.getString("designation_code"),
                rs.getString("designation_title"), rs.getString("scale_grade"), rs.getString("ro_code"), rs.getString("ro_name"),
                rs.getString("dpc_code"), rs.getString("dpc_name"), rs.getString("active_bank_name"), rs.getString("active_bank_branch"),
                rs.getString("active_bank_account_no"), rs.getString("active_bank_ifsc"), rs.getString("bank_verification_status"),
                rs.getString("social_category"), (Boolean) rs.getObject("is_pwbd"), rs.getString("disability_type"),
                toLocalDate(rs.getDate("superannuation_date")), (Boolean) rs.getObject("is_board_director"),
                rs.getString("superannuation_calculation_basis"), rs.getString("pension_settlement_status"));
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
}
