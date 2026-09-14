package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One rule-change audit entry (Part 3/31 of the spec: "Every rule modification must have: user, timestamp,
 * old value, new value, reason...") - binds to the already-live cpf_rule_audit_log (0 rows so far). Distinct
 * from this codebase's generic {@code in.gov.jci.hrms.audit.Auditable}/AuditLogRecorder mechanism (used
 * elsewhere in this codebase) because this table already existed with its own shape before any Java code
 * did - {@code CpfWithdrawalRuleService} writes to it directly rather than retrofitting the generic
 * mechanism onto a UUID-keyed entity it wasn't designed for.
 */
@Entity
@Table(name = "cpf_rule_audit_log")
public class CpfRuleAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private CpfWithdrawalRuleVersion version;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state")
    private String previousState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state")
    private String newState;

    @Column(name = "performed_by", nullable = false, length = 64)
    private String performedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfRuleAuditLog() {
    }

    public CpfRuleAuditLog(CpfWithdrawalRuleVersion version, String action, String previousState, String newState,
                            String performedBy, String reason) {
        this.version = version;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.performedBy = performedBy;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public CpfWithdrawalRuleVersion getVersion() {
        return version;
    }

    public String getAction() {
        return action;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
