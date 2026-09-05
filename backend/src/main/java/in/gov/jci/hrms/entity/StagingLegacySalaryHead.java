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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** staging_salary_month_id is a plain FK to the staging row, not a JPA association - mirrors StagingLegacyLoanTransaction's relationship to StagingLegacyLoan. */
@Entity
@Table(name = "staging_legacy_salary_heads")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacySalaryHead implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staging_salary_month_id", nullable = false)
    private Long stagingSalaryMonthId;

    @Column(name = "salary_head_code", nullable = false, length = 20)
    private String salaryHeadCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "head_category", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private HeadCategory headCategory;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacySalaryHead() {
    }

    public StagingLegacySalaryHead(Long stagingSalaryMonthId, String salaryHeadCode, HeadCategory headCategory, BigDecimal amount) {
        this.stagingSalaryMonthId = stagingSalaryMonthId;
        this.salaryHeadCode = salaryHeadCode;
        this.headCategory = headCategory;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getStagingSalaryMonthId() {
        return stagingSalaryMonthId;
    }

    public String getSalaryHeadCode() {
        return salaryHeadCode;
    }

    public HeadCategory getHeadCategory() {
        return headCategory;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public StagingRowStatus getStatus() {
        return status;
    }

    public void setStatus(StagingRowStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "StagingLegacySalaryHead";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stagingSalaryMonthId", stagingSalaryMonthId);
        snapshot.put("salaryHeadCode", salaryHeadCode);
        snapshot.put("headCategory", headCategory);
        snapshot.put("amount", amount);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
