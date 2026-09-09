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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Professional Tax Slabs Master (JCI Payroll Engine) - bracketed by state (state_master.state_code),
 * with an optional peak-surcharge month (e.g. February for WB, March for Assam/Odisha/Bihar).
 */
@Entity
@Table(name = "payroll_ptax_slabs")
@EntityListeners(AuditableEntityListener.class)
public class PtaxSlab implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_code", nullable = false, length = 20)
    private String stateCode;

    @Column(name = "slab_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal slabMin;

    @Column(name = "slab_max", precision = 12, scale = 2)
    private BigDecimal slabMax;

    @Column(name = "tax_amount", nullable = false, precision = 8, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "special_month")
    private Integer specialMonth;

    @Column(name = "special_month_tax", precision = 8, scale = 2)
    private BigDecimal specialMonthTax;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2020, 4, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected PtaxSlab() {
    }

    public PtaxSlab(String stateCode, BigDecimal slabMin, BigDecimal slabMax, BigDecimal taxAmount,
                     Integer specialMonth, BigDecimal specialMonthTax, LocalDate effectiveFrom) {
        this.stateCode = stateCode;
        this.slabMin = slabMin;
        this.slabMax = slabMax;
        this.taxAmount = taxAmount;
        this.specialMonth = specialMonth;
        this.specialMonthTax = specialMonthTax;
        if (effectiveFrom != null) {
            this.effectiveFrom = effectiveFrom;
        }
    }

    public Long getId() {
        return id;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public BigDecimal getSlabMin() {
        return slabMin;
    }

    public void setSlabMin(BigDecimal slabMin) {
        this.slabMin = slabMin;
    }

    public BigDecimal getSlabMax() {
        return slabMax;
    }

    public void setSlabMax(BigDecimal slabMax) {
        this.slabMax = slabMax;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public Integer getSpecialMonth() {
        return specialMonth;
    }

    public void setSpecialMonth(Integer specialMonth) {
        this.specialMonth = specialMonth;
    }

    public BigDecimal getSpecialMonthTax() {
        return specialMonthTax;
    }

    public void setSpecialMonthTax(BigDecimal specialMonthTax) {
        this.specialMonthTax = specialMonthTax;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "PtaxSlab";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stateCode", stateCode);
        snapshot.put("slabMin", slabMin);
        snapshot.put("slabMax", slabMax);
        snapshot.put("taxAmount", taxAmount);
        return snapshot;
    }
}
