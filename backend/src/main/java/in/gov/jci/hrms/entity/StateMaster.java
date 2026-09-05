package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Matches the shared dev database's already-live schema exactly: a UUID
 * primary key (gen_random_uuid()) and no soft-delete columns - only
 * is_active + created_at, unlike most master entities in this codebase.
 * "Delete" therefore means deactivating (is_active = false), not a
 * deleted_at-based soft delete.
 */
@Entity
@Table(name = "state_master")
public class StateMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, length = 10)
    private String stateCode;

    @Column(name = "state_name", nullable = false, length = 100)
    private String stateName;

    @Convert(converter = StateTypeConverter.class)
    @Column(name = "state_type", nullable = false, length = 50)
    private StateType stateType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StateMaster() {
    }

    public StateMaster(String stateCode, String stateName, StateType stateType, boolean active) {
        this.stateCode = stateCode;
        this.stateName = stateName;
        this.stateType = stateType;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public StateType getStateType() {
        return stateType;
    }

    public void setStateType(StateType stateType) {
        this.stateType = stateType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
