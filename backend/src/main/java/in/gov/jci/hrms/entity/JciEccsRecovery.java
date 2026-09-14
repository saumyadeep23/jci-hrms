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
import java.time.LocalDate;

/**
 * JCIECCS Lifecycle Engine Phase 1 - the RECOVERY record: what was actually received (payroll debit,
 * cash deposit, or a reversal of an earlier recovery), independent of both what was DEMANDED
 * ({@link JciEccsCollectionDetail}) and what has been POSTED to the ledger/balance (its
 * {@link JciEccsRecoveryAllocation} rows and the {@link JciEccsLoanRepayment} rows they produce).
 * {@code status} only reaches POSTED once {@code JciEccsRecoveryPostingService} has actually written the
 * ledger/schedule/outstanding-balance effects - a recovery existing does not itself mean money moved
 * anywhere. Binds to jcieccs_recovery (V90).
 */
@Entity
@Table(name = "jcieccs_recovery")
public class JciEccsRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private JciEccsLoan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private JciEccsRecoverySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JciEccsRecoveryStatus status = JciEccsRecoveryStatus.PENDING;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private HrmsPayrollCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_detail_id")
    private JciEccsCollectionDetail collectionDetail;

    @Column(name = "payroll_transaction_reference", length = 100)
    private String payrollTransactionReference;

    @Column(name = "receipt_number", length = 100)
    private String receiptNumber;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_recovery_id")
    private JciEccsRecovery reversalOfRecovery;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected JciEccsRecovery() {
    }

    public JciEccsRecovery(JciEccsMember member, JciEccsLoan loan, JciEccsRecoverySource source, BigDecimal grossAmount,
                            LocalDate paymentDate, HrmsPayrollCycle cycle, JciEccsCollectionDetail collectionDetail,
                            String payrollTransactionReference, String receiptNumber, String idempotencyKey,
                            JciEccsRecovery reversalOfRecovery, String remarks, Long createdBy) {
        this.member = member;
        this.loan = loan;
        this.source = source;
        this.grossAmount = grossAmount;
        this.paymentDate = paymentDate;
        this.cycle = cycle;
        this.collectionDetail = collectionDetail;
        this.payrollTransactionReference = payrollTransactionReference;
        this.receiptNumber = receiptNumber;
        this.idempotencyKey = idempotencyKey;
        this.reversalOfRecovery = reversalOfRecovery;
        this.remarks = remarks;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public JciEccsMember getMember() {
        return member;
    }

    public JciEccsLoan getLoan() {
        return loan;
    }

    public JciEccsRecoverySource getSource() {
        return source;
    }

    public JciEccsRecoveryStatus getStatus() {
        return status;
    }

    public void setStatus(JciEccsRecoveryStatus status) {
        this.status = status;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public HrmsPayrollCycle getCycle() {
        return cycle;
    }

    public JciEccsCollectionDetail getCollectionDetail() {
        return collectionDetail;
    }

    public String getPayrollTransactionReference() {
        return payrollTransactionReference;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public JciEccsRecovery getReversalOfRecovery() {
        return reversalOfRecovery;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(Instant reversedAt) {
        this.reversedAt = reversedAt;
    }
}
