package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One manual edit of a payroll_monthly_head_items amount (V78) - see PayrollBatchEditService. A BASIC edit writes one row for BASIC itself plus one row per cascaded head (DA/HRA/CPF/JCPF/P.Tax). */
@Entity
@Table(name = "payroll_edit_logs")
public class PayrollEditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private PayrollBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tran_id", nullable = false)
    private PayrollMonthlyRecord record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "head_count", nullable = false)
    private int headCount;

    @Column(name = "old_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "change_reason", nullable = false, length = 500)
    private String changeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edited_by")
    private Employee editedBy;

    @CreationTimestamp
    @Column(name = "edited_at", nullable = false, updatable = false)
    private Instant editedAt;

    protected PayrollEditLog() {
    }

    public PayrollEditLog(PayrollBatch batch, PayrollMonthlyRecord record, Employee employee, int headCount,
                           BigDecimal oldAmount, BigDecimal newAmount, String changeReason, Employee editedBy) {
        this.batch = batch;
        this.record = record;
        this.employee = employee;
        this.headCount = headCount;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
        this.changeReason = changeReason;
        this.editedBy = editedBy;
    }

    public Long getId() {
        return id;
    }

    public PayrollBatch getBatch() {
        return batch;
    }

    public PayrollMonthlyRecord getRecord() {
        return record;
    }

    public Employee getEmployee() {
        return employee;
    }

    public int getHeadCount() {
        return headCount;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public Employee getEditedBy() {
        return editedBy;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
