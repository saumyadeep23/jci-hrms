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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The locked, immutable per-payroll-run collection snapshot (spec section 1.4). payroll_run_id is a
 * plain opaque string business key supplied by Payroll - no FK to payroll_batches, keeping this module
 * decoupled from that schema's own internals (confirmed: the already-live DB has no such FK either).
 * Binds to the already-live jcieccs_collection_batch (V87).
 */
@Entity
@Table(name = "jcieccs_collection_batch")
public class JciEccsCollectionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_run_id", nullable = false, length = 50)
    private String payrollRunId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private HrmsPayrollCycle cycle;

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @Column(name = "calculated_by")
    private Long calculatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false, length = 30)
    private JciEccsCollectionBatchStatus batchStatus = JciEccsCollectionBatchStatus.LOCKED;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "debit_confirmed_at")
    private Instant debitConfirmedAt;

    @Column(name = "total_expected_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalExpectedAmount = BigDecimal.ZERO;

    @Column(name = "total_debited_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDebitedAmount = BigDecimal.ZERO;

    protected JciEccsCollectionBatch() {
    }

    public JciEccsCollectionBatch(String payrollRunId, HrmsPayrollCycle cycle, Long calculatedBy, BigDecimal totalExpectedAmount) {
        this.payrollRunId = payrollRunId;
        this.cycle = cycle;
        this.calculatedBy = calculatedBy;
        this.totalExpectedAmount = totalExpectedAmount;
        this.lockedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPayrollRunId() {
        return payrollRunId;
    }

    public HrmsPayrollCycle getCycle() {
        return cycle;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public Long getCalculatedBy() {
        return calculatedBy;
    }

    public JciEccsCollectionBatchStatus getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(JciEccsCollectionBatchStatus batchStatus) {
        this.batchStatus = batchStatus;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getDebitConfirmedAt() {
        return debitConfirmedAt;
    }

    public void setDebitConfirmedAt(Instant debitConfirmedAt) {
        this.debitConfirmedAt = debitConfirmedAt;
    }

    public BigDecimal getTotalExpectedAmount() {
        return totalExpectedAmount;
    }

    public void setTotalExpectedAmount(BigDecimal totalExpectedAmount) {
        this.totalExpectedAmount = totalExpectedAmount;
    }

    public BigDecimal getTotalDebitedAmount() {
        return totalDebitedAmount;
    }

    public void setTotalDebitedAmount(BigDecimal totalDebitedAmount) {
        this.totalDebitedAmount = totalDebitedAmount;
    }
}
