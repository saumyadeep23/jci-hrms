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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No soft-delete columns (no deleted_at/@SQLRestriction) - matches the
 * exact V8 migration schema, which only lists id/scale_type/effective_from/
 * da_percentage/is_active for this table.
 */
@Entity
@Table(name = "da_rate_history")
@EntityListeners(AuditableEntityListener.class)
public class DaRateHistory implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 3)
    private ScaleType scaleType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Server-computed only (DaRateHistoryService.create()) - null means "still current", never client-supplied. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "da_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal daPercentage;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "remarks", length = 255)
    private String remarks;

    protected DaRateHistory() {
    }

    /** Kept exactly as-is (PayrollComputationServiceTest constructs DaRateHistory this way) - delegates with nulls for the newer fields. */
    public DaRateHistory(ScaleType scaleType, LocalDate effectiveFrom, BigDecimal daPercentage, boolean active) {
        this(scaleType, effectiveFrom, null, daPercentage, active, null, null, null);
    }

    public DaRateHistory(ScaleType scaleType, LocalDate effectiveFrom, LocalDate effectiveTo, BigDecimal daPercentage,
                          boolean active, String orderNumber, LocalDate orderDate, String remarks) {
        this.scaleType = scaleType;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.daPercentage = daPercentage;
        this.active = active;
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.remarks = remarks;
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

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public BigDecimal getDaPercentage() {
        return daPercentage;
    }

    public void setDaPercentage(BigDecimal daPercentage) {
        this.daPercentage = daPercentage;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String auditEntityName() {
        return "DaRateHistory";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scaleType", scaleType);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        snapshot.put("daPercentage", daPercentage);
        snapshot.put("active", active);
        snapshot.put("orderNumber", orderNumber);
        snapshot.put("orderDate", orderDate);
        snapshot.put("remarks", remarks);
        return snapshot;
    }
}
