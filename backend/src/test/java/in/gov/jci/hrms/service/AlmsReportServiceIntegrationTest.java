package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CcsForm1Response;
import in.gov.jci.hrms.dto.Circular53ComplianceRow;
import in.gov.jci.hrms.dto.EncashmentRegisterRow;
import in.gov.jci.hrms.dto.MusterRollRow;
import in.gov.jci.hrms.dto.PayrollCutoffFeedRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB smoke test for AlmsReportService's raw JdbcTemplate SQL - compile
 * success only proves the Java is well-formed, not that every column/join
 * name in these hand-written queries is correct against the live schema.
 * Runs against the same shared dev DB every other real-DB test in this suite
 * uses (see StateMasterRepositoryTest/AuditLogRepositoryTest).
 */
@SpringBootTest
class AlmsReportServiceIntegrationTest {

    @Autowired
    private AlmsReportService almsReportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void generateMusterRoll_doesNotThrowAndCountsAddUp() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        Page<MusterRollRow> page = almsReportService.generateMusterRoll(start, end, null, null, PageRequest.of(0, 10));

        assertThat(page).isNotNull();
        for (MusterRollRow row : page.getContent()) {
            assertThat(row.dailyPunches()).hasSize(row.totalCycleDays());
            assertThat(row.totalCycleDays()).isEqualTo(7);
        }
    }

    @Test
    void generatePayrollFeed_doesNotThrow() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);

        List<PayrollCutoffFeedRow> rows = almsReportService.generatePayrollFeed(start, end, null);

        assertThat(rows).isNotNull();
    }

    @Test
    void generateCircular53Report_doesNotThrow() {
        List<Circular53ComplianceRow> rows = almsReportService.generateCircular53Report(YearMonth.now(), null);

        assertThat(rows).isNotNull();
    }

    @Test
    void generateCcsForm1_forAnyRealEmployee_doesNotThrow() {
        Long employeeId = jdbcTemplate.queryForObject("SELECT id FROM employees WHERE deleted_at IS NULL LIMIT 1", Long.class);
        assertThat(employeeId).as("fixture requires at least one employee row").isNotNull();

        CcsForm1Response response = almsReportService.generateCcsForm1(employeeId, LocalDate.now().getYear());

        assertThat(response).isNotNull();
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.totalBalance()).isEqualByComparingTo(response.closingEnjoyable().add(response.closingEncashable()));
    }

    @Test
    void generateEncashmentRegister_doesNotThrow() {
        List<EncashmentRegisterRow> rows = almsReportService.generateEncashmentRegister(LocalDate.now().minusYears(2), LocalDate.now());

        assertThat(rows).isNotNull();
    }
}
