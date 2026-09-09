package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** HRA Rate Master (JCI Payroll Engine) - HRA percentage + minimum floor amount by city class (X/Y/Z), versioned by effective date range. */
@Entity
@Table(name = "payroll_hra_rates")
@EntityListeners(AuditableEntityListener.class)
public class PayrollHraRate implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_class", nullable = false, length = 5)
    private String cityClass;

    @Column(name = "rate_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercentage;

    @Column(name = "min_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2020, 4, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PayrollHraRate() {
    }

    public PayrollHraRate(String cityClass, BigDecimal ratePercentage, BigDecimal minAmount, LocalDate effectiveFrom,
                           LocalDate effectiveTo, String remarks) {
        this.cityClass = cityClass;
        this.ratePercentage = ratePercentage;
        if (minAmount != null) {
            this.minAmount = minAmount;
        }
        if (effectiveFrom != null) {
            this.effectiveFrom = effectiveFrom;
        }
        this.effectiveTo = effectiveTo;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getCityClass() {
        return cityClass;
    }

    public void setCityClass(String cityClass) {
        this.cityClass = cityClass;
    }

    public BigDecimal getRatePercentage() {
        return ratePercentage;
    }

    public void setRatePercentage(BigDecimal ratePercentage) {
        this.ratePercentage = ratePercentage;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "PayrollHraRate";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cityClass", cityClass);
        snapshot.put("ratePercentage", ratePercentage);
        snapshot.put("minAmount", minAmount);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        return snapshot;
    }
}
