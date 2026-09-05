package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;

/**
 * A named shift (HO-RO/SHIFT_A/SHIFT_B/SHIFT_C/DPC_WD/DPC_SAT, ...) for
 * Watchmen/Security/General rosters - distinct from office_timing_config
 * (V36), which is a single global grace-window config for regular-staff
 * attendance evaluation, not a multi-shift roster. shift_code uniqueness is
 * enforced by a partial unique index (WHERE deleted_at IS NULL) in the
 * Flyway migration, matching every other simple master here.
 *
 * ShiftResolutionService is this schema's only consumer of full/half day
 * minutes and applicable office type - see its javadoc for how a shift is
 * picked per employee/day.
 */
@Entity
@Table(name = "shift_master")
@SQLRestriction("deleted_at IS NULL")
public class ShiftMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_code", nullable = false, length = 20)
    private String shiftCode;

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "grace_period_minutes", nullable = false)
    private Integer gracePeriodMinutes;

    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight;

    /** Minimum worked minutes to count as a full/half day - null for a shift (e.g. a watchmen rotation) with no such policy defined yet. */
    @Column(name = "full_day_minutes")
    private Integer fullDayMinutes;

    @Column(name = "half_day_minutes")
    private Integer halfDayMinutes;

    /** 'HEAD_OFFICE' / 'REGIONAL_OFFICE' / 'DPC' - which office type this shift is meant for, or null if not tied to one (e.g. a watchmen rotation, assigned via roster override instead). Informational only - ShiftResolutionService picks shifts by shift_code, not this tag. */
    @Column(name = "applicable_office_type", length = 20)
    private String applicableOfficeType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ShiftMaster() {
    }

    public ShiftMaster(String shiftCode, String shiftName, LocalTime startTime, LocalTime endTime,
                        Integer gracePeriodMinutes, boolean crossesMidnight, boolean active) {
        this.shiftCode = shiftCode;
        this.shiftName = shiftName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.crossesMidnight = crossesMidnight;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getShiftCode() {
        return shiftCode;
    }

    public void setShiftCode(String shiftCode) {
        this.shiftCode = shiftCode;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getGracePeriodMinutes() {
        return gracePeriodMinutes;
    }

    public void setGracePeriodMinutes(Integer gracePeriodMinutes) {
        this.gracePeriodMinutes = gracePeriodMinutes;
    }

    public boolean isCrossesMidnight() {
        return crossesMidnight;
    }

    public void setCrossesMidnight(boolean crossesMidnight) {
        this.crossesMidnight = crossesMidnight;
    }

    public Integer getFullDayMinutes() {
        return fullDayMinutes;
    }

    public void setFullDayMinutes(Integer fullDayMinutes) {
        this.fullDayMinutes = fullDayMinutes;
    }

    public Integer getHalfDayMinutes() {
        return halfDayMinutes;
    }

    public void setHalfDayMinutes(Integer halfDayMinutes) {
        this.halfDayMinutes = halfDayMinutes;
    }

    public String getApplicableOfficeType() {
        return applicableOfficeType;
    }

    public void setApplicableOfficeType(String applicableOfficeType) {
        this.applicableOfficeType = applicableOfficeType;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
