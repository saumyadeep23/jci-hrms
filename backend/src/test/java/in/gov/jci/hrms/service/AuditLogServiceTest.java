package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AuditLogResponse;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.AuditLog;
import in.gov.jci.hrms.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
    }

    @Test
    void getAuditLogs_passesFiltersThroughAndMapsResults() {
        AuditLog log = new AuditLog("Employee", 1L, AuditAction.UPDATE, "hr.admin", "127.0.0.1", "{}", "{}");
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.search(eq("Employee"), eq(1L), eq("hr.admin"), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        Page<AuditLogResponse> result = auditLogService.getAuditLogs(
                "Employee", 1L, "hr.admin", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).entityName()).isEqualTo("Employee");
        assertThat(result.getContent().get(0).actingUsername()).isEqualTo("hr.admin");
    }

    @Test
    void getAuditLogs_convertsDateRangeToInclusiveUtcInstantBounds() {
        Pageable pageable = PageRequest.of(0, 20);
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        when(auditLogRepository.search(isNull(), isNull(), isNull(), fromCaptor.capture(), toCaptor.capture(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        auditLogService.getAuditLogs(null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), pageable);

        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        // Inclusive end-of-day: last nanosecond of Jan 31, not midnight of Feb 1.
        assertThat(toCaptor.getValue()).isBefore(LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(toCaptor.getValue()).isAfter(LocalDate.of(2026, 1, 31).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void getAuditLogs_withNoDateFilters_passesNullBounds() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AuditLogResponse> result = auditLogService.getAuditLogs(null, null, null, null, null, pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
