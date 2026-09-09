package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Matches the shared dev database's already-live schema exactly: a UUID
 * primary key (gen_random_uuid()) and "delete" means deactivating
 * (is_active = false), not a deleted_at-based soft delete - StateMasterService
 * never sets deleted_at, though V67 added the column (unused here) since it
 * was already live. is_remote_area/remote_allowance_percentage back the
 * Remote Area Allowance toggle on the State Master screen - see V67's own
 * chk_state_remote_allowance_rule for the DB-level version of the rule
 * StateMasterService also validates (false => 0.00; true => (0.00, 100.00]).
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

    @Column(name = "is_remote_area", nullable = false)
    private boolean remoteArea = false;

    @Column(name = "remote_allowance_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal remoteAllowancePercentage = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public boolean isRemoteArea() {
        return remoteArea;
    }

    public void setRemoteArea(boolean remoteArea) {
        this.remoteArea = remoteArea;
    }

    public BigDecimal getRemoteAllowancePercentage() {
        return remoteAllowancePercentage;
    }

    public void setRemoteAllowancePercentage(BigDecimal remoteAllowancePercentage) {
        this.remoteAllowancePercentage = remoteAllowancePercentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
