package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.SeparatedEmployeeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB smoke test, matching CadreStrengthReportServiceIntegrationTest's
 * pattern. Employee id 101 (Shib Kumar Sah) is RETIRED in the dev dataset -
 * this is the concrete case the "Separated Staff" tab exists to surface,
 * and Manpower4TierReportServiceIntegrationTest is the paired assertion
 * that the same employee is excluded from "active manpower" totals.
 */
@SpringBootTest
class SeparatedEmployeeDirectoryServiceIntegrationTest {

    @Autowired
    private SeparatedEmployeeDirectoryService separatedEmployeeDirectoryService;

    @Test
    void list_includesShibKumarSah_whoIsRetired() {
        var page = separatedEmployeeDirectoryService.list(null, null, PageRequest.of(0, 500));

        assertThat(page.getContent())
                .extracting(SeparatedEmployeeResponse::id)
                .contains(101L);
    }

    @Test
    void list_statusFilterRetired_includesShibKumarSahOnly() {
        var page = separatedEmployeeDirectoryService.list("RETIRED", null, PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting(SeparatedEmployeeResponse::id).contains(101L);
    }

    @Test
    void list_statusFilterResigned_excludesShibKumarSah() {
        var page = separatedEmployeeDirectoryService.list("RESIGNED", null, PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting(SeparatedEmployeeResponse::id).doesNotContain(101L);
    }

    /**
     * Regression for the exact bug SeparatedStaffTab.tsx hit: it sends
     * status="SEPARATED" as a meta-keyword (matching the EmployeeSpecification
     * convention on the sibling /api/employees endpoint's directory filter),
     * but this service's SQL originally did a literal e.status = 'SEPARATED'
     * equality check - no employee ever has that literal status, so both the
     * count and data queries always returned zero rows and the tab rendered
     * empty regardless of what was actually in the database.
     */
    @Test
    void list_statusFilterSeparated_isTreatedAsNoOpKeyword_notLiteralStatusValue() {
        var page = separatedEmployeeDirectoryService.list("SEPARATED", null, PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting(SeparatedEmployeeResponse::id).contains(101L);
    }

    @Test
    void list_statusFilterAll_isTreatedAsNoOpKeyword() {
        var page = separatedEmployeeDirectoryService.list("ALL", null, PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting(SeparatedEmployeeResponse::id).contains(101L);
    }
}
