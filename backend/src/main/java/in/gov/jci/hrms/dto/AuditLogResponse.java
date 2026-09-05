package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String entityName,
        Long entityId,
        AuditAction action,
        String actingUsername,
        String clientIp,
        String beforeState,
        String afterState,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityName(),
                log.getEntityId(),
                log.getAction(),
                log.getActingUsername(),
                log.getClientIp(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getCreatedAt()
        );
    }
}
