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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (cycle_year, cycle_month) uniqueness is enforced by a plain unique
 * constraint in the V8 migration (no soft-delete on this table). cycle_month
 * names the run by the month its 25th falls in, e.g. "August 2026" runs
 * 2026-07-26 to 2026-08-25 - see PayrollComputationService.deriveCycleDates.
 */
@Entity
@Table(name = "payroll_runs")
@EntityListeners(AuditableEntityListener.class)
public class PayrollRun implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Column(name = "cycle_month", nullable = false)
    private Integer cycleMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

    @Column(name = "finalized_by", length = 150)
    private String finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private PayrollRunType runType = PayrollRunType.LIVE;

    @Column(name = "is_migrated", nullable = false)
    private boolean migrated = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PayrollRun() {
    }

    public PayrollRun(Integer cycleYear, Integer cycleMonth, LocalDate startDate, LocalDate endDate) {
        this.cycleYear = cycleYear;
        this.cycleMonth = cycleMonth;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public Integer getCycleYear() {
        return cycleYear;
    }

    public Integer getCycleMonth() {
        return cycleMonth;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PayrollRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollRunStatus status) {
        this.status = status;
    }

    public String getFinalizedBy() {
        return finalizedBy;
    }

    public void setFinalizedBy(String finalizedBy) {
        this.finalizedBy = finalizedBy;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PayrollRunType getRunType() {
        return runType;
    }

    public void setRunType(PayrollRunType runType) {
        this.runType = runType;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void setMigrated(boolean migrated) {
        this.migrated = migrated;
    }

    @Override
    public String auditEntityName() {
        return "PayrollRun";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cycleYear", cycleYear);
        snapshot.put("cycleMonth", cycleMonth);
        snapshot.put("startDate", startDate);
        snapshot.put("endDate", endDate);
        snapshot.put("status", status);
        snapshot.put("finalizedBy", finalizedBy);
        snapshot.put("finalizedAt", finalizedAt);
        snapshot.put("runType", runType);
        snapshot.put("isMigrated", migrated);
        return snapshot;
    }
}
