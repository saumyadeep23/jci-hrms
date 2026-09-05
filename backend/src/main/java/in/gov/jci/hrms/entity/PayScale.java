package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * (scale_type, grade) uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration. IDA (Industrial
 * Dearness Allowance / 3rd PRC) is the primary scale type; CDA (7th CPC)
 * is legacy-only.
 */
@Entity
@Table(name = "pay_scale_master")
@SQLRestriction("deleted_at IS NULL")
public class PayScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 3)
    private ScaleType scaleType;

    @Column(name = "grade", nullable = false, length = 20)
    private String grade;

    @Column(name = "minimum_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumBasic;

    @Column(name = "maximum_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal maximumBasic;

    @Column(name = "increment_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal incrementRate;

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

    protected PayScale() {
    }

    public PayScale(ScaleType scaleType, String grade, BigDecimal minimumBasic, BigDecimal maximumBasic,
                     BigDecimal incrementRate, boolean active) {
        this.scaleType = scaleType;
        this.grade = grade;
        this.minimumBasic = minimumBasic;
        this.maximumBasic = maximumBasic;
        this.incrementRate = incrementRate;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public BigDecimal getMinimumBasic() {
        return minimumBasic;
    }

    public void setMinimumBasic(BigDecimal minimumBasic) {
        this.minimumBasic = minimumBasic;
    }

    public BigDecimal getMaximumBasic() {
        return maximumBasic;
    }

    public void setMaximumBasic(BigDecimal maximumBasic) {
        this.maximumBasic = maximumBasic;
    }

    public BigDecimal getIncrementRate() {
        return incrementRate;
    }

    public void setIncrementRate(BigDecimal incrementRate) {
        this.incrementRate = incrementRate;
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
