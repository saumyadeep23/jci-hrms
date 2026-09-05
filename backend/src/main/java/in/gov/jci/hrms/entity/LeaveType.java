package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * code uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration.
 */
@Entity
@Table(name = "leave_types")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class LeaveType implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "annual_quota", nullable = false, precision = 4, scale = 1)
    private BigDecimal annualQuota;

    @Column(name = "max_accumulation_days")
    private Integer maxAccumulationDays;

    @Column(name = "is_encashable", nullable = false)
    private boolean encashable = false;

    @Column(name = "career_limit_days")
    private Integer careerLimitDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** V37 - which EmploymentCategory values may apply for/be baselined against this leave type. Empty only transiently before the master-data UI's first save; the V37 seed leaves every pre-existing leave type eligible for all 4 categories. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "leave_type_cadre_eligibility", joinColumns = @JoinColumn(name = "leave_type_id"))
    @Column(name = "employment_category", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<EmploymentCategory> eligibleCategories = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LeaveType() {
    }

    public LeaveType(String code, String name, BigDecimal annualQuota, boolean encashable, boolean active) {
        this.code = code;
        this.name = name;
        this.annualQuota = annualQuota;
        this.encashable = encashable;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAnnualQuota() {
        return annualQuota;
    }

    public void setAnnualQuota(BigDecimal annualQuota) {
        this.annualQuota = annualQuota;
    }

    public Integer getMaxAccumulationDays() {
        return maxAccumulationDays;
    }

    public void setMaxAccumulationDays(Integer maxAccumulationDays) {
        this.maxAccumulationDays = maxAccumulationDays;
    }

    public boolean isEncashable() {
        return encashable;
    }

    public void setEncashable(boolean encashable) {
        this.encashable = encashable;
    }

    public Integer getCareerLimitDays() {
        return careerLimitDays;
    }

    public void setCareerLimitDays(Integer careerLimitDays) {
        this.careerLimitDays = careerLimitDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<EmploymentCategory> getEligibleCategories() {
        return eligibleCategories;
    }

    public void setEligibleCategories(Set<EmploymentCategory> eligibleCategories) {
        this.eligibleCategories = eligibleCategories;
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
        return "LeaveType";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", code);
        snapshot.put("name", name);
        snapshot.put("annualQuota", annualQuota);
        snapshot.put("maxAccumulationDays", maxAccumulationDays);
        snapshot.put("isEncashable", encashable);
        snapshot.put("careerLimitDays", careerLimitDays);
        snapshot.put("active", active);
        snapshot.put("eligibleCategories", eligibleCategories);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
