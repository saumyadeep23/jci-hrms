package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsLifecycleEvent;

import java.time.Instant;

/** Phase 5 (spec sections 22, 29) - the free-form JCIECCS lifecycle audit trail (JciEccsLifecycleEvent,
 * V87), exposed read-only per entity so an administrator can trace a loan/recovery/reconciliation's full
 * history from its own detail screen. */
public record JciEccsAuditEventResponse(
        Long id,
        String entityType,
        Long entityId,
        String eventType,
        Instant eventDate,
        String oldValue,
        String newValue,
        String referenceId,
        Long performedBy,
        String remarks
) {
    public static JciEccsAuditEventResponse from(JciEccsLifecycleEvent e) {
        return new JciEccsAuditEventResponse(e.getId(), e.getEntityType(), e.getEntityId(), e.getEventType(), e.getEventDate(),
                e.getOldValue(), e.getNewValue(), e.getReferenceId(), e.getPerformedBy(), e.getRemarks());
    }
}
