package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * code uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration, which also seeds the
 * seven core loan types. interest_rate_annual/max_installments are
 * placeholders, not confirmed JCI loan policy - see LoanService javadoc.
 */
@Entity
@Table(name = "loan_type_master")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class LoanType implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private LoanTypeCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "interest_rate_annual", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRateAnnual;

    @Column(name = "max_installments", nullable = false)
    private Integer maxInstallments;

    @Column(name = "is_reducing_balance", nullable = false)
    private boolean reducingBalance = true;

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

    protected LoanType() {
    }

    public LoanType(LoanTypeCode code, String name, BigDecimal interestRateAnnual, Integer maxInstallments,
                     boolean reducingBalance, boolean active) {
        this.code = code;
        this.name = name;
        this.interestRateAnnual = interestRateAnnual;
        this.maxInstallments = maxInstallments;
        this.reducingBalance = reducingBalance;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public LoanTypeCode getCode() {
        return code;
    }

    public void setCode(LoanTypeCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getInterestRateAnnual() {
        return interestRateAnnual;
    }

    public void setInterestRateAnnual(BigDecimal interestRateAnnual) {
        this.interestRateAnnual = interestRateAnnual;
    }

    public Integer getMaxInstallments() {
        return maxInstallments;
    }

    public void setMaxInstallments(Integer maxInstallments) {
        this.maxInstallments = maxInstallments;
    }

    public boolean isReducingBalance() {
        return reducingBalance;
    }

    public void setReducingBalance(boolean reducingBalance) {
        this.reducingBalance = reducingBalance;
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
        return "LoanType";
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
        snapshot.put("interestRateAnnual", interestRateAnnual);
        snapshot.put("maxInstallments", maxInstallments);
        snapshot.put("isReducingBalance", reducingBalance);
        snapshot.put("active", active);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
