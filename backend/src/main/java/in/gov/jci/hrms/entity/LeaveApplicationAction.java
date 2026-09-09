package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One immutable row per routing/decision step on a LeaveApplication - never updated, only appended to (see LeaveApplicationService). */
@Entity
@Table(name = "leave_application_actions")
public class LeaveApplicationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LeaveApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_by", nullable = false)
    private Employee actionBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private LeaveActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forwarded_to")
    private Employee forwardedTo;

    @Column(name = "remarks")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaveApplicationAction() {
    }

    public LeaveApplicationAction(LeaveApplication application, Employee actionBy, LeaveActionType actionType,
                                   Employee forwardedTo, String remarks) {
        this.application = application;
        this.actionBy = actionBy;
        this.actionType = actionType;
        this.forwardedTo = forwardedTo;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public LeaveApplication getApplication() {
        return application;
    }

    public Employee getActionBy() {
        return actionBy;
    }

    public LeaveActionType getActionType() {
        return actionType;
    }

    public Employee getForwardedTo() {
        return forwardedTo;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
