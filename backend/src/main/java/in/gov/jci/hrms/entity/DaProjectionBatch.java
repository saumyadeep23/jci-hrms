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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One IDA/CDA DA-rate-revision simulation/order (table pre-exists this entity - see V-whatever migration
 * that created da_projection_batches/employees/monthly_breakups, same "table before entity" situation as
 * EmployeeCeaClaim). expectedDrawalMonth/Year and retroMonthsCount are mutable - checkCutoffAndRollOver()
 * bumps both when the target drawal month's payroll_batches row is already locked or the 25th-cutoff has
 * passed (IdaProjectionEngineService's own javadoc has the full Scenario-A rule).
 */
@Entity
@Table(name = "da_projection_batches")
@EntityListeners(AuditableEntityListener.class)
public class DaProjectionBatch implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "projection_code", nullable = false, length = 50, unique = true)
    private String projectionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 20)
    private ScaleType scaleType;

    @Column(name = "old_da_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal oldDaRate;

    @Column(name = "new_da_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal newDaRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "expected_drawal_month", nullable = false)
    private int expectedDrawalMonth;

    @Column(name = "expected_drawal_year", nullable = false)
    private int expectedDrawalYear;

    @Column(name = "retro_months_count", nullable = false)
    private int retroMonthsCount;

    @Column(name = "total_active_employees", nullable = false)
    private int totalActiveEmployees;

    @Column(name = "total_monthly_gross_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMonthlyGrossDelta = BigDecimal.ZERO;

    @Column(name = "total_monthly_employer_cost_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMonthlyEmployerCostDelta = BigDecimal.ZERO;

    @Column(name = "total_arrear_gross_outgo", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalArrearGrossOutgo = BigDecimal.ZERO;

    @Column(name = "total_arrear_net_outgo", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalArrearNetOutgo = BigDecimal.ZERO;

    @Column(name = "total_employer_cost_outgo", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEmployerCostOutgo = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DaProjectionBatchStatus status = DaProjectionBatchStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected DaProjectionBatch() {
    }

    public DaProjectionBatch(String projectionCode, ScaleType scaleType, BigDecimal oldDaRate, BigDecimal newDaRate,
                              LocalDate effectiveFrom, int expectedDrawalMonth, int expectedDrawalYear,
                              int retroMonthsCount, Employee createdBy) {
        this.projectionCode = projectionCode;
        this.scaleType = scaleType;
        this.oldDaRate = oldDaRate;
        this.newDaRate = newDaRate;
        this.effectiveFrom = effectiveFrom;
        this.expectedDrawalMonth = expectedDrawalMonth;
        this.expectedDrawalYear = expectedDrawalYear;
        this.retroMonthsCount = retroMonthsCount;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getProjectionCode() {
        return projectionCode;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public BigDecimal getOldDaRate() {
        return oldDaRate;
    }

    public BigDecimal getNewDaRate() {
        return newDaRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public int getExpectedDrawalMonth() {
        return expectedDrawalMonth;
    }

    public void setExpectedDrawalMonth(int expectedDrawalMonth) {
        this.expectedDrawalMonth = expectedDrawalMonth;
    }

    public int getExpectedDrawalYear() {
        return expectedDrawalYear;
    }

    public void setExpectedDrawalYear(int expectedDrawalYear) {
        this.expectedDrawalYear = expectedDrawalYear;
    }

    public int getRetroMonthsCount() {
        return retroMonthsCount;
    }

    public void setRetroMonthsCount(int retroMonthsCount) {
        this.retroMonthsCount = retroMonthsCount;
    }

    public int getTotalActiveEmployees() {
        return totalActiveEmployees;
    }

    public void setTotalActiveEmployees(int totalActiveEmployees) {
        this.totalActiveEmployees = totalActiveEmployees;
    }

    public BigDecimal getTotalMonthlyGrossDelta() {
        return totalMonthlyGrossDelta;
    }

    public void setTotalMonthlyGrossDelta(BigDecimal totalMonthlyGrossDelta) {
        this.totalMonthlyGrossDelta = totalMonthlyGrossDelta;
    }

    public BigDecimal getTotalMonthlyEmployerCostDelta() {
        return totalMonthlyEmployerCostDelta;
    }

    public void setTotalMonthlyEmployerCostDelta(BigDecimal totalMonthlyEmployerCostDelta) {
        this.totalMonthlyEmployerCostDelta = totalMonthlyEmployerCostDelta;
    }

    public BigDecimal getTotalArrearGrossOutgo() {
        return totalArrearGrossOutgo;
    }

    public void setTotalArrearGrossOutgo(BigDecimal totalArrearGrossOutgo) {
        this.totalArrearGrossOutgo = totalArrearGrossOutgo;
    }

    public BigDecimal getTotalArrearNetOutgo() {
        return totalArrearNetOutgo;
    }

    public void setTotalArrearNetOutgo(BigDecimal totalArrearNetOutgo) {
        this.totalArrearNetOutgo = totalArrearNetOutgo;
    }

    public BigDecimal getTotalEmployerCostOutgo() {
        return totalEmployerCostOutgo;
    }

    public void setTotalEmployerCostOutgo(BigDecimal totalEmployerCostOutgo) {
        this.totalEmployerCostOutgo = totalEmployerCostOutgo;
    }

    public DaProjectionBatchStatus getStatus() {
        return status;
    }

    public void setStatus(DaProjectionBatchStatus status) {
        this.status = status;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "DaProjectionBatch";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectionCode", projectionCode);
        snapshot.put("scaleType", scaleType);
        snapshot.put("oldDaRate", oldDaRate);
        snapshot.put("newDaRate", newDaRate);
        snapshot.put("expectedDrawalMonth", expectedDrawalMonth);
        snapshot.put("expectedDrawalYear", expectedDrawalYear);
        snapshot.put("retroMonthsCount", retroMonthsCount);
        snapshot.put("status", status);
        return snapshot;
    }
}
