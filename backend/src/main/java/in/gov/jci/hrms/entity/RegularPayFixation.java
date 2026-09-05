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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A REGULAR employee's historized basic-pay ledger (V50) - one row per fixation event
 * (INITIAL_APPOINTMENT, PROMOTION, ...), only one is_current=true row per employee at a time
 * (idx_uq_current_regular_fixation). Additive alongside employee_employment_categories.regular_basic_pay
 * (V29), which MovementOrderService keeps in sync on promotion so payroll's existing reader is
 * unaffected - see that migration's header comment for why both exist.
 */
@Entity
@Table(name = "regular_pay_fixations")
@EntityListeners(AuditableEntityListener.class)
public class RegularPayFixation implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_code", referencedColumnName = "scale_code", nullable = false)
    private GradeScaleMaster gradeScale;

    @Column(name = "basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "increment_cycle", nullable = false, length = 10)
    private IncrementCycle incrementCycle = IncrementCycle.JULY;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixation_reason", nullable = false, length = 100)
    private FixationReason fixationReason = FixationReason.INITIAL_APPOINTMENT;

    @Column(name = "order_ref_no", length = 100)
    private String orderRefNo;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RegularPayFixation() {
    }

    public RegularPayFixation(Employee employee, GradeScaleMaster gradeScale, BigDecimal basicPay, LocalDate effectiveFrom) {
        this.employee = employee;
        this.gradeScale = gradeScale;
        this.basicPay = basicPay;
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public void setBasicPay(BigDecimal basicPay) {
        this.basicPay = basicPay;
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

    public IncrementCycle getIncrementCycle() {
        return incrementCycle;
    }

    public void setIncrementCycle(IncrementCycle incrementCycle) {
        this.incrementCycle = incrementCycle;
    }

    public FixationReason getFixationReason() {
        return fixationReason;
    }

    public void setFixationReason(FixationReason fixationReason) {
        this.fixationReason = fixationReason;
    }

    public String getOrderRefNo() {
        return orderRefNo;
    }

    public void setOrderRefNo(String orderRefNo) {
        this.orderRefNo = orderRefNo;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    @Override
    public String auditEntityName() {
        return "RegularPayFixation";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("scaleCode", gradeScale != null ? gradeScale.getScaleCode() : null);
        snapshot.put("basicPay", basicPay);
        snapshot.put("fixationReason", fixationReason);
        snapshot.put("current", current);
        return snapshot;
    }
}
