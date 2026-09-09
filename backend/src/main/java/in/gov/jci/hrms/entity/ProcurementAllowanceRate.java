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

/** Procurement Allowance Master (JCI Payroll Engine) - monthly allowance for field-cadre designations engaged in procurement duties. */
@Entity
@Table(name = "payroll_procurement_allowance_rates")
@EntityListeners(AuditableEntityListener.class)
public class ProcurementAllowanceRate implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @Column(name = "monthly_allowance", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAllowance;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2020, 4, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected ProcurementAllowanceRate() {
    }

    public ProcurementAllowanceRate(Designation designation, BigDecimal monthlyAllowance, LocalDate effectiveFrom) {
        this.designation = designation;
        this.monthlyAllowance = monthlyAllowance;
        if (effectiveFrom != null) {
            this.effectiveFrom = effectiveFrom;
        }
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

    public BigDecimal getMonthlyAllowance() {
        return monthlyAllowance;
    }

    public void setMonthlyAllowance(BigDecimal monthlyAllowance) {
        this.monthlyAllowance = monthlyAllowance;
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
        return "ProcurementAllowanceRate";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("designationId", designation != null ? designation.getId() : null);
        snapshot.put("monthlyAllowance", monthlyAllowance);
        snapshot.put("effectiveFrom", effectiveFrom);
        return snapshot;
    }
}
