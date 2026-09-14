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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payroll Computation Engine's monthly batch - a distinct, newer concept from the pre-existing
 * PayrollRun/payroll_runs (cycle_year/cycle_month, DRAFT/COMPUTED/FINALIZED, feeds leave-encashment
 * arrears/loan repayments/payslips). payroll_batches is the batch-and-line-item schema
 * PayrollComputationService.processBatch() computes into (payroll_monthly_records +
 * payroll_monthly_head_items), with its own HR-finalize/Finance-approve workflow - the two are not
 * merged here since PayrollRun already has real, separate integrations this migration doesn't touch.
 */
@Entity
@Table(name = "payroll_batches")
@EntityListeners(AuditableEntityListener.class)
public class PayrollBatch implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, length = 50)
    private String batchNo;

    @Column(name = "sal_month", nullable = false)
    private int salMonth;

    @Column(name = "sal_year", nullable = false)
    private int salYear;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayrollBatchStatus status = PayrollBatchStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 20)
    private PayrollBatchType batchType = PayrollBatchType.REGULAR;

    @Column(name = "pay_date")
    private java.time.LocalDate payDate;

    @Column(name = "total_employees", nullable = false)
    private int totalEmployees;

    @Column(name = "total_gross", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_finalized_by")
    private Employee hrFinalizedBy;

    @Column(name = "hr_finalized_at")
    private Instant hrFinalizedAt;

    @Column(name = "hr_remarks", columnDefinition = "TEXT")
    private String hrRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finance_approved_by")
    private Employee financeApprovedBy;

    @Column(name = "finance_approved_at")
    private Instant financeApprovedAt;

    @Column(name = "finance_remarks", columnDefinition = "TEXT")
    private String financeRemarks;

    @Column(name = "voucher_ref_no", length = 50)
    private String voucherRefNo;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PayrollBatch() {
    }

    public PayrollBatch(String batchNo, int salMonth, int salYear, String financialYear) {
        this.batchNo = batchNo;
        this.salMonth = salMonth;
        this.salYear = salYear;
        this.financialYear = financialYear;
    }

    public Long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public int getSalMonth() {
        return salMonth;
    }

    public int getSalYear() {
        return salYear;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public PayrollBatchStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollBatchStatus status) {
        this.status = status;
    }

    public PayrollBatchType getBatchType() {
        return batchType;
    }

    public void setBatchType(PayrollBatchType batchType) {
        this.batchType = batchType;
    }

    public java.time.LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(java.time.LocalDate payDate) {
        this.payDate = payDate;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(int totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public void setTotalGross(BigDecimal totalGross) {
        this.totalGross = totalGross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public void setTotalNet(BigDecimal totalNet) {
        this.totalNet = totalNet;
    }

    public Employee getHrFinalizedBy() {
        return hrFinalizedBy;
    }

    public void setHrFinalizedBy(Employee hrFinalizedBy) {
        this.hrFinalizedBy = hrFinalizedBy;
    }

    public Instant getHrFinalizedAt() {
        return hrFinalizedAt;
    }

    public void setHrFinalizedAt(Instant hrFinalizedAt) {
        this.hrFinalizedAt = hrFinalizedAt;
    }

    public String getHrRemarks() {
        return hrRemarks;
    }

    public Employee getFinanceApprovedBy() {
        return financeApprovedBy;
    }

    public Instant getFinanceApprovedAt() {
        return financeApprovedAt;
    }

    public String getFinanceRemarks() {
        return financeRemarks;
    }

    public String getVoucherRefNo() {
        return voucherRefNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "PayrollBatch";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("batchNo", batchNo);
        snapshot.put("status", status);
        snapshot.put("totalEmployees", totalEmployees);
        snapshot.put("totalGross", totalGross);
        snapshot.put("totalNet", totalNet);
        return snapshot;
    }
}
