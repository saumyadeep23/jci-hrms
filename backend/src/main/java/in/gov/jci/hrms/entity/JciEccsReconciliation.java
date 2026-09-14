package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JCIECCS Lifecycle Engine Phase 2 - one three-way reconciliation result (DEMAND vs ACTUAL RECOVERY vs
 * LEDGER POSTING) for one (collection_detail, component) pair. Detects discrepancies only - this entity
 * and the service that writes it never mutate loan/schedule/ledger/thrift state; resolution only records
 * that an authorised action was taken (possibly via a real workflow like reversal), never silently
 * "fixes" the numbers. Binds to jcieccs_reconciliation (V91).
 */
@Entity
@Table(name = "jcieccs_reconciliation")
public class JciEccsReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_run_id", length = 50)
    private String payrollRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_batch_id")
    private JciEccsCollectionBatch collectionBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_detail_id")
    private JciEccsCollectionDetail collectionDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private JciEccsLoan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", length = 30)
    private JciEccsRecoveryComponent component;

    @Column(name = "expected_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedAmount = BigDecimal.ZERO;

    @Column(name = "actual_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(name = "posted_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal postedAmount = BigDecimal.ZERO;

    @Column(name = "variance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal varianceAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JciEccsReconciliationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private JciEccsReconciliationReasonCode reasonCode;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", length = 40)
    private JciEccsReconciliationResolutionAction resolutionAction;

    @Column(name = "resolution_remarks", length = 500)
    private String resolutionRemarks;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JciEccsReconciliation() {
    }

    public JciEccsReconciliation(String payrollRunId, JciEccsCollectionBatch collectionBatch, JciEccsCollectionDetail collectionDetail,
                                  JciEccsMember member, Long employeeId, JciEccsLoan loan, JciEccsRecoveryComponent component,
                                  BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal postedAmount,
                                  JciEccsReconciliationStatus status, JciEccsReconciliationReasonCode reasonCode) {
        this.payrollRunId = payrollRunId;
        this.collectionBatch = collectionBatch;
        this.collectionDetail = collectionDetail;
        this.member = member;
        this.employeeId = employeeId;
        this.loan = loan;
        this.component = component;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.postedAmount = postedAmount;
        this.varianceAmount = expectedAmount.subtract(postedAmount);
        this.status = status;
        this.reasonCode = reasonCode;
        this.detectedAt = Instant.now();
    }

    /** Refreshes a repeat reconciliation run's amounts/status onto an already-existing, not-yet-resolved
     * row (the upsert half of "safely repeatable, never duplicated"). Never called on a resolved row. */
    public void refresh(BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal postedAmount,
                         JciEccsReconciliationStatus status, JciEccsReconciliationReasonCode reasonCode) {
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.postedAmount = postedAmount;
        this.varianceAmount = expectedAmount.subtract(postedAmount);
        this.status = status;
        this.reasonCode = reasonCode;
        this.detectedAt = Instant.now();
    }

    public void resolve(JciEccsReconciliationResolutionAction action, String remarks, Long resolvedByEmployeeId) {
        this.resolved = true;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedByEmployeeId;
        this.resolutionAction = action;
        this.resolutionRemarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getPayrollRunId() {
        return payrollRunId;
    }

    public JciEccsCollectionBatch getCollectionBatch() {
        return collectionBatch;
    }

    public JciEccsCollectionDetail getCollectionDetail() {
        return collectionDetail;
    }

    public JciEccsMember getMember() {
        return member;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public JciEccsLoan getLoan() {
        return loan;
    }

    public JciEccsRecoveryComponent getComponent() {
        return component;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public BigDecimal getPostedAmount() {
        return postedAmount;
    }

    public BigDecimal getVarianceAmount() {
        return varianceAmount;
    }

    public JciEccsReconciliationStatus getStatus() {
        return status;
    }

    public JciEccsReconciliationReasonCode getReasonCode() {
        return reasonCode;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public JciEccsReconciliationResolutionAction getResolutionAction() {
        return resolutionAction;
    }

    public String getResolutionRemarks() {
        return resolutionRemarks;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
