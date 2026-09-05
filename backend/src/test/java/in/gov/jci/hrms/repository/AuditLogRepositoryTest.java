package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Diagnostic real-DB regression test for the dashboard's GET /api/audit-logs?size=1 500. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("search() with all-null filters (the dashboard's GET ?size=1 call) does not throw")
    void search_withAllNullFilters_returnsFirstPageWithoutThrowing() {
        Page<AuditLog> page = auditLogRepository.search(null, null, null, null, null, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isGreaterThan(0);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("search() with a fromDate/toDate range still filters correctly once cast, not just when null")
    void search_withDateRange_filtersToThatWindow() {
        Instant farFuture = Instant.now().plus(3650, ChronoUnit.DAYS);

        Page<AuditLog> page = auditLogRepository.search(null, null, null, farFuture, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("search() with an entityName filter narrows results to that entity")
    void search_withEntityNameFilter_onlyReturnsMatchingRows() {
        Page<AuditLog> page = auditLogRepository.search("Employee", null, null, null, null, PageRequest.of(0, 50));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(log -> assertThat(log.getEntityName()).isEqualTo("Employee"));
    }
}
