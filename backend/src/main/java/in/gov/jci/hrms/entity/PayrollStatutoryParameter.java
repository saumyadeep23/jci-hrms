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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statutory Parameters console (JCI Payroll Engine) - versioned CPF/EPS/NPS/GIS/LWF rates and flat
 * amounts, one row per (param_key, effective_from). Only one row per key should ever have a null
 * effective_to ("current") at a time - PayrollStatutoryParameterServiceImpl.revise() enforces that by
 * closing out the current row's effective_to the instant it inserts the new one, never leaving a gap
 * or an overlap.
 */
@Entity
@Table(name = "payroll_statutory_parameters")
@EntityListeners(AuditableEntityListener.class)
public class PayrollStatutoryParameter implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "param_key", nullable = false, length = 50)
    private String paramKey;

    @Column(name = "param_name", nullable = false, length = 150)
    private String paramName;

    @Column(name = "param_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal paramValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "val_type", nullable = false, length = 20)
    private StatutoryParamValueType valType = StatutoryParamValueType.DECIMAL;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2020, 4, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PayrollStatutoryParameter() {
    }

    public PayrollStatutoryParameter(String paramKey, String paramName, BigDecimal paramValue, StatutoryParamValueType valType,
                                      LocalDate effectiveFrom, String remarks) {
        this.paramKey = paramKey;
        this.paramName = paramName;
        this.paramValue = paramValue;
        if (valType != null) {
            this.valType = valType;
        }
        if (effectiveFrom != null) {
            this.effectiveFrom = effectiveFrom;
        }
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getParamKey() {
        return paramKey;
    }

    public String getParamName() {
        return paramName;
    }

    public BigDecimal getParamValue() {
        return paramValue;
    }

    public StatutoryParamValueType getValType() {
        return valType;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "PayrollStatutoryParameter";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paramKey", paramKey);
        snapshot.put("paramValue", paramValue);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        return snapshot;
    }
}
