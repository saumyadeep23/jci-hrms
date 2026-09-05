package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CadreStrengthReportResponse;
import in.gov.jci.hrms.dto.PimsReportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB smoke test for CadreStrengthReportService's raw JdbcTemplate SQL,
 * matching AlmsReportServiceIntegrationTest's pattern - compile success only
 * proves the Java is well-formed, not that the hand-written SQL is valid
 * against the live PostgreSQL server.
 *
 * generate_noFilters_doesNotThrow reproduces the exact UAT report: GET
 * /api/v1/reports/pims/cadre-strength with every filter null (the report's
 * own default, no-filter landing state - see CadreStrengthTab.tsx) used to
 * fail every time with "ERROR: could not determine data type of parameter
 * $1" before CadreStrengthReportService's bare "? IS NULL" placeholders were
 * given an explicit CAST(? AS BIGINT).
 */
@SpringBootTest
class CadreStrengthReportServiceIntegrationTest {

    @Autowired
    private CadreStrengthReportService cadreStrengthReportService;

    @Test
    void generate_noFilters_doesNotThrow() {
        CadreStrengthReportResponse response = cadreStrengthReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.byLocation()).isNotNull();
        assertThat(response.overall()).isNotNull();
        assertThat(response.overall().sanctioned()).isGreaterThanOrEqualTo(0);
        long sumSanctioned = response.byLocation().stream().mapToLong(row -> row.sanctioned()).sum();
        assertThat(response.overall().sanctioned()).isEqualTo(sumSanctioned);
    }

    @Test
    void generate_withNonExistentDepartmentFilter_returnsEmptyRatherThanThrowing() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, null, null, null, null);

        CadreStrengthReportResponse response = cadreStrengthReportService.generate(filter);

        assertThat(response.byLocation()).isEmpty();
        assertThat(response.overall().sanctioned()).isZero();
        assertThat(response.overall().occupancyPercentage()).isEqualByComparingTo("0");
    }
}
