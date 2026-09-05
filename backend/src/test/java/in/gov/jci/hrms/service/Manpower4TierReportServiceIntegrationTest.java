package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.Manpower4TierReportResponse;
import in.gov.jci.hrms.dto.ManpowerTierRow;
import in.gov.jci.hrms.dto.PimsReportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB smoke/regression test for Manpower4TierReportService's raw
 * JdbcTemplate SQL, matching CadreStrengthReportServiceIntegrationTest's
 * pattern. The REGULAR-tier assertions pin the exact totalCost/averageCost
 * this dev dataset's 200 ACTIVE REGULAR employees must produce once REGULAR
 * basic pay is sourced from regular_pay_fixations (the historized, increment/
 * promotion-aware ledger) rather than employee_employment_categories.regular_basic_pay
 * (a point-in-time snapshot from initial appointment that a REGULAR
 * employee's later increments/promotions never update in place) - a
 * regression back to the old join would silently under-report cost by
 * ~28% (51,69,030 vs 71,24,280) without this test catching it.
 *
 * <p>Headcount is 200, not 201: employee id 101 (Shib Kumar Sah) is
 * RETIRED but still has an active REGULAR employee_employment_categories
 * row (separation doesn't clean that table up - see
 * EmployeeReleaseService), so the query's e.status = 'ACTIVE' filter
 * (added alongside the exit/terminal-settlement work) is what now excludes
 * him from this "active manpower" total - without that filter this would
 * be 201 / 71,94,200.00.
 */
@SpringBootTest
class Manpower4TierReportServiceIntegrationTest {

    @Autowired
    private Manpower4TierReportService manpower4TierReportService;

    @Test
    void generate_noFilters_doesNotThrow() {
        Manpower4TierReportResponse response = manpower4TierReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.tiers()).isNotEmpty();
        assertThat(response.totalHeadcount()).isEqualTo(response.tiers().stream().mapToLong(ManpowerTierRow::headcount).sum());
    }

    @Test
    void generate_regularTier_sourcesBasicPayFromCurrentRegularPayFixation() {
        Manpower4TierReportResponse response = manpower4TierReportService.generate(PimsReportFilter.empty());

        Optional<ManpowerTierRow> regular = response.tiers().stream()
                .filter(t -> "REGULAR".equals(t.employmentCategory()))
                .findFirst();

        assertThat(regular).isPresent();
        assertThat(regular.get().headcount()).isEqualTo(200);
        assertThat(regular.get().totalCost()).isEqualByComparingTo(new BigDecimal("7124280.00"));
        assertThat(regular.get().averageCost()).isEqualByComparingTo(new BigDecimal("35621.40"));
    }

    @Test
    void generate_withNonExistentDepartmentFilter_returnsEmptyRatherThanThrowing() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, null, null, null, null);

        Manpower4TierReportResponse response = manpower4TierReportService.generate(filter);

        assertThat(response.tiers()).isEmpty();
        assertThat(response.totalHeadcount()).isZero();
    }
}
