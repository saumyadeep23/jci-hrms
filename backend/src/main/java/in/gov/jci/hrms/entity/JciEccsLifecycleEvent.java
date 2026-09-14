package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Free-form audit trail across every JCIECCS entity kind - a dedicated table rather than a retrofit of
 * the generic Auditable/AuditLog mechanism (Long-keyed CRUD-snapshot only), same rationale as
 * CpfRuleAuditLog. old_value/new_value follow this codebase's existing JSON-string-column convention
 * (see CpfApplication.calculationTrace) - plain String fields, manually (de)serialized via ObjectMapper,
 * mapped to the DB's real JSONB columns via @JdbcTypeCode(SqlTypes.JSON). Append-only, never updated.
 * Binds to the already-live jcieccs_lifecycle_event (V87).
 */
@Entity
@Table(name = "jcieccs_lifecycle_event")
public class JciEccsLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @CreationTimestamp
    @Column(name = "event_date", nullable = false, updatable = false)
    private Instant eventDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    protected JciEccsLifecycleEvent() {
    }

    public JciEccsLifecycleEvent(String entityType, Long entityId, String eventType, String oldValue, String newValue,
                                  String referenceId, Long performedBy, String remarks) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.referenceId = referenceId;
        this.performedBy = performedBy;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Long getPerformedBy() {
        return performedBy;
    }

    public String getRemarks() {
        return remarks;
    }
}
