package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AuditLogResponse;
import in.gov.jci.hrms.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Read-only search over the append-only audit_logs table (see AuditLog /
 * AuditableEntityListener). fromDate/toDate are inclusive calendar-day
 * bounds in UTC, matching this app's fixed -Duser.timezone=UTC test/build
 * posture - there is no per-user timezone concept anywhere in this schema.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR_ADMIN')")
    public Page<AuditLogResponse> getAuditLogs(String entityName, Long entityId, String performedBy,
                                                LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Instant from = fromDate != null ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant to = toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1) : null;
        return auditLogRepository.search(entityName, entityId, performedBy, from, to, pageable)
                .map(AuditLogResponse::from);
    }
}
