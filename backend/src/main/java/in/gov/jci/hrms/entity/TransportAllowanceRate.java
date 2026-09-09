package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport Allowance Master (JCI Payroll Engine) - a rate keyed by grade scale and city class (X/Y/Z).
 * Maps the pre-existing payroll_transport_allowance_rates table (created directly against the shared
 * dev database before this entity existed, same situation as state_master - see V66's own comment) -
 * notably no designation column: the live rate structure is grade-scale + city-class only.
 */
@Entity
@Table(name = "payroll_transport_allowance_rates")
@EntityListeners(AuditableEntityListener.class)
public class TransportAllowanceRate implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_scale_id")
    private GradeScaleMaster gradeScale;

    @Column(name = "city_class", nullable = false, length = 5)
    private String cityClass;

    @Column(name = "base_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2020, 4, 1);

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected TransportAllowanceRate() {
    }

    public TransportAllowanceRate(GradeScaleMaster gradeScale, String cityClass, BigDecimal baseRate, LocalDate effectiveFrom) {
        this.gradeScale = gradeScale;
        this.cityClass = cityClass;
        this.baseRate = baseRate;
        if (effectiveFrom != null) {
            this.effectiveFrom = effectiveFrom;
        }
    }

    public Long getId() {
        return id;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public void setGradeScale(GradeScaleMaster gradeScale) {
        this.gradeScale = gradeScale;
    }

    public String getCityClass() {
        return cityClass;
    }

    public void setCityClass(String cityClass) {
        this.cityClass = cityClass;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "TransportAllowanceRate";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("gradeScaleId", gradeScale != null ? gradeScale.getId() : null);
        snapshot.put("cityClass", cityClass);
        snapshot.put("baseRate", baseRate);
        snapshot.put("effectiveFrom", effectiveFrom);
        return snapshot;
    }
}
