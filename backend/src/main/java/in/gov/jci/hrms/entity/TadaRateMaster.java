package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (designation_id, city_class) uniqueness is enforced by a partial unique
 * index (WHERE deleted_at IS NULL) in the Flyway migration.
 */
@Entity
@Table(name = "tada_rate_master")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class TadaRateMaster implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "city_class", nullable = false, length = 1, columnDefinition = "VARCHAR")
    private CityClass cityClass;

    @Column(name = "room_rent_ceiling", nullable = false, precision = 10, scale = 2)
    private BigDecimal roomRentCeiling;

    @Column(name = "daily_allowance_ceiling", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyAllowanceCeiling;

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

    protected TadaRateMaster() {
    }

    public TadaRateMaster(Designation designation, CityClass cityClass, BigDecimal roomRentCeiling,
                           BigDecimal dailyAllowanceCeiling, boolean active) {
        this.designation = designation;
        this.cityClass = cityClass;
        this.roomRentCeiling = roomRentCeiling;
        this.dailyAllowanceCeiling = dailyAllowanceCeiling;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public CityClass getCityClass() {
        return cityClass;
    }

    public void setCityClass(CityClass cityClass) {
        this.cityClass = cityClass;
    }

    public BigDecimal getRoomRentCeiling() {
        return roomRentCeiling;
    }

    public void setRoomRentCeiling(BigDecimal roomRentCeiling) {
        this.roomRentCeiling = roomRentCeiling;
    }

    public BigDecimal getDailyAllowanceCeiling() {
        return dailyAllowanceCeiling;
    }

    public void setDailyAllowanceCeiling(BigDecimal dailyAllowanceCeiling) {
        this.dailyAllowanceCeiling = dailyAllowanceCeiling;
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

    @Override
    public String auditEntityName() {
        return "TadaRateMaster";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("designationId", designation != null ? designation.getId() : null);
        snapshot.put("cityClass", cityClass);
        snapshot.put("roomRentCeiling", roomRentCeiling);
        snapshot.put("dailyAllowanceCeiling", dailyAllowanceCeiling);
        snapshot.put("active", active);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
