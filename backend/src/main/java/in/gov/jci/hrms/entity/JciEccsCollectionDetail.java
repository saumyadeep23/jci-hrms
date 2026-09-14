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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per employee per {@link JciEccsCollectionBatch} - the read-only, locked line item Payroll
 * imports verbatim as deductions (spec section 1.4: Payroll administrators cannot edit these values).
 * term_principal/emergency_principal are INTEGER at the DB level (whole-rupee ₹10-multiples), matching
 * jcieccs_loan_schedule.principal_due. Binds to the already-live jcieccs_collection_detail (V87).
 */
@Entity
@Table(name = "jcieccs_collection_detail")
public class JciEccsCollectionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private JciEccsCollectionBatch batch;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @Column(name = "thrift_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal thriftAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_loan_id")
    private JciEccsLoan termLoan;

    @Column(name = "term_principal", nullable = false)
    private int termPrincipal;

    @Column(name = "term_interest", nullable = false, precision = 14, scale = 2)
    private BigDecimal termInterest = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_loan_id")
    private JciEccsLoan emergencyLoan;

    @Column(name = "emergency_principal", nullable = false)
    private int emergencyPrincipal;

    @Column(name = "emergency_interest", nullable = false, precision = 14, scale = 2)
    private BigDecimal emergencyInterest = BigDecimal.ZERO;

    @Column(name = "total_snapshot_amount", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalSnapshotAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_status", nullable = false, length = 30)
    private JciEccsDebitStatus debitStatus = JciEccsDebitStatus.PENDING_DEBIT;

    @Column(name = "actual_debited_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualDebitedAmount = BigDecimal.ZERO;

    @Column(name = "payroll_transaction_id", length = 100)
    private String payrollTransactionId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reconciliation_reason", length = 60)
    private String reconciliationReason;

    protected JciEccsCollectionDetail() {
    }

    public JciEccsCollectionDetail(JciEccsCollectionBatch batch, Long employeeId, JciEccsMember member, BigDecimal thriftAmount,
                                    JciEccsLoan termLoan, int termPrincipal, BigDecimal termInterest,
                                    JciEccsLoan emergencyLoan, int emergencyPrincipal, BigDecimal emergencyInterest) {
        this.batch = batch;
        this.employeeId = employeeId;
        this.member = member;
        this.thriftAmount = thriftAmount;
        this.termLoan = termLoan;
        this.termPrincipal = termPrincipal;
        this.termInterest = termInterest;
        this.emergencyLoan = emergencyLoan;
        this.emergencyPrincipal = emergencyPrincipal;
        this.emergencyInterest = emergencyInterest;
    }

    public Long getId() {
        return id;
    }

    public JciEccsCollectionBatch getBatch() {
        return batch;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public JciEccsMember getMember() {
        return member;
    }

    public BigDecimal getThriftAmount() {
        return thriftAmount;
    }

    public JciEccsLoan getTermLoan() {
        return termLoan;
    }

    public int getTermPrincipal() {
        return termPrincipal;
    }

    public BigDecimal getTermInterest() {
        return termInterest;
    }

    public JciEccsLoan getEmergencyLoan() {
        return emergencyLoan;
    }

    public int getEmergencyPrincipal() {
        return emergencyPrincipal;
    }

    public BigDecimal getEmergencyInterest() {
        return emergencyInterest;
    }

    public BigDecimal getTotalSnapshotAmount() {
        return totalSnapshotAmount;
    }

    public JciEccsDebitStatus getDebitStatus() {
        return debitStatus;
    }

    public void setDebitStatus(JciEccsDebitStatus debitStatus) {
        this.debitStatus = debitStatus;
    }

    public BigDecimal getActualDebitedAmount() {
        return actualDebitedAmount;
    }

    public void setActualDebitedAmount(BigDecimal actualDebitedAmount) {
        this.actualDebitedAmount = actualDebitedAmount;
    }

    public String getPayrollTransactionId() {
        return payrollTransactionId;
    }

    public void setPayrollTransactionId(String payrollTransactionId) {
        this.payrollTransactionId = payrollTransactionId;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public String getReconciliationReason() {
        return reconciliationReason;
    }

    public void setReconciliationReason(String reconciliationReason) {
        this.reconciliationReason = reconciliationReason;
    }
}
