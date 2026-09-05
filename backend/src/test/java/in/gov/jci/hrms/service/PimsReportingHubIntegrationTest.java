package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AdHocReportRequest;
import in.gov.jci.hrms.dto.AparMatrixReportResponse;
import in.gov.jci.hrms.dto.IncrementDueEntry;
import in.gov.jci.hrms.dto.Manpower4TierReportResponse;
import in.gov.jci.hrms.dto.PimsReportFilter;
import in.gov.jci.hrms.dto.ReservationRosterReportResponse;
import in.gov.jci.hrms.dto.SuperannuationReportResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB smoke test for the PIMS Reporting Hub's remaining raw-JdbcTemplate
 * report services, matching CadreStrengthReportServiceIntegrationTest's
 * pattern - compile success only proves the Java is well-formed, not that
 * the hand-written SQL is valid against the live PostgreSQL server.
 *
 * Every one of these, called with the report's own default (no filter
 * selected) state, used to fail with "ERROR: could not determine data type
 * of parameter $1": each service's "(? IS NULL OR column = ?)" optional
 * department filter left a bare "? IS NULL" with no typed sibling for
 * PostgreSQL to infer a type from. Fixed with an explicit cast in each -
 * CAST(? AS BIGINT) for a Long filter (departmentId), CAST(? AS VARCHAR) for
 * a String one (AdHocReportService's employmentCategory/socialCategory).
 * See CadreStrengthReportService's own fix/test for the first instance of
 * this bug class.
 */
@SpringBootTest
class PimsReportingHubIntegrationTest {

    @Autowired
    private SuperannuationReportService superannuationReportService;

    @Autowired
    private ReservationRosterReportService reservationRosterReportService;

    @Autowired
    private Manpower4TierReportService manpower4TierReportService;

    @Autowired
    private AparMatrixReportService aparMatrixReportService;

    @Autowired
    private IncrementProcessingService incrementProcessingService;

    @Autowired
    private AdHocReportService adHocReportService;

    @Test
    void superannuation_noFilters_doesNotThrow() {
        SuperannuationReportResponse response = superannuationReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.windowSummary()).isNotNull();
        assertThat(response.entries()).isNotNull();
    }

    @Test
    void superannuation_withDepartmentFilter_doesNotThrow() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, 60, null, null, null);

        SuperannuationReportResponse response = superannuationReportService.generate(filter);

        assertThat(response.entries()).isEmpty();
    }

    @Test
    void reservationRoster_noFilters_doesNotThrow() {
        ReservationRosterReportResponse response = reservationRosterReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.rows()).isNotNull();
        assertThat(response.totalEmployees()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reservationRoster_withNonExistentDepartmentFilter_returnsZeroedRatherThanThrowing() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, null, null, null, null);

        ReservationRosterReportResponse response = reservationRosterReportService.generate(filter);

        assertThat(response.rows()).isEmpty();
        assertThat(response.totalEmployees()).isZero();
        assertThat(response.pwbdCount()).isZero();
    }

    @Test
    void manpower4Tier_noFilters_doesNotThrow() {
        Manpower4TierReportResponse response = manpower4TierReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.tiers()).isNotNull();
        assertThat(response.totalHeadcount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aparMatrix_noFilters_doesNotThrow() {
        AparMatrixReportResponse response = aparMatrixReportService.generate(PimsReportFilter.empty());

        assertThat(response).isNotNull();
        assertThat(response.rows()).isNotNull();
    }

    @Test
    void incrementDueList_noFilters_returnsCleanlyAndNeverNull() {
        List<IncrementDueEntry> entries = incrementProcessingService.dueList(PimsReportFilter.empty());

        assertThat(entries).isNotNull();
    }

    @Test
    void incrementDueList_withNonExistentDepartmentFilter_returnsEmptyRatherThanThrowing() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, null, null, null, null);

        List<IncrementDueEntry> entries = incrementProcessingService.dueList(filter);

        assertThat(entries).isEmpty();
    }

    @Test
    void adHoc_noFilters_doesNotThrow() {
        AdHocReportRequest request = new AdHocReportRequest(List.of("employee_code", "full_name"), PimsReportFilter.empty(), 0, 25);

        TabularReportResponse response = adHocReportService.generate(request);

        assertThat(response).isNotNull();
        assertThat(response.rows()).isNotNull();
    }

    @Test
    void adHoc_withDepartmentFilter_doesNotThrow() {
        PimsReportFilter filter = new PimsReportFilter(-1L, null, null, null, null, null, null, null, null, null, null);
        AdHocReportRequest request = new AdHocReportRequest(List.of("employee_code", "full_name"), filter, 0, 25);

        TabularReportResponse response = adHocReportService.generate(request);

        assertThat(response.rows()).isEmpty();
    }

    @Test
    void adHoc_withEmploymentCategoryFilter_doesNotThrow() {
        PimsReportFilter filter = new PimsReportFilter(null, null, null, null, "OUTSOURCED", null, null, null, null, null, null);
        AdHocReportRequest request = new AdHocReportRequest(List.of("employee_code", "full_name"), filter, 0, 25);

        TabularReportResponse response = adHocReportService.generate(request);

        assertThat(response.rows()).isNotNull();
    }

    @Test
    void adHoc_withSocialCategoryFilter_doesNotThrow() {
        PimsReportFilter filter = new PimsReportFilter(null, null, null, null, null, "ST", null, null, null, null, null);
        AdHocReportRequest request = new AdHocReportRequest(List.of("employee_code", "full_name"), filter, 0, 25);

        TabularReportResponse response = adHocReportService.generate(request);

        assertThat(response.rows()).isNotNull();
    }
}
