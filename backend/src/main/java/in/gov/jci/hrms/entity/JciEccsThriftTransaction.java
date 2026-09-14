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
 * The thrift ledger - independent of any loan (a member with no loans still contributes thrift every
 * cycle). amount is always a strictly positive magnitude (DB CHECK chk_jcieccs_th_amt); direction is
 * encoded by transaction_type (REFUND decreases balance_after, everything else increases it), not by
 * sign - this schema never stores negative ledger amounts. Binds to the already-live
 * jcieccs_thrift_transaction (V87).
 */
@Entity
@Table(name = "jcieccs_thrift_transaction")
public class JciEccsThriftTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_detail_id")
    private JciEccsCollectionDetail collectionDetail;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private HrmsPayrollCycle cycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private JciEccsThriftTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private JciEccsThriftTransactionSource source;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected JciEccsThriftTransaction() {
    }

    public JciEccsThriftTransaction(JciEccsMember member, Long employeeId, JciEccsCollectionDetail collectionDetail,
                                     LocalDate transactionDate, HrmsPayrollCycle cycle, JciEccsThriftTransactionType transactionType,
                                     BigDecimal amount, JciEccsThriftTransactionSource source, BigDecimal balanceAfter, String remarks) {
        this.member = member;
        this.employeeId = employeeId;
        this.collectionDetail = collectionDetail;
        this.transactionDate = transactionDate;
        this.cycle = cycle;
        this.transactionType = transactionType;
        this.amount = amount;
        this.source = source;
        this.balanceAfter = balanceAfter;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public JciEccsMember getMember() {
        return member;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public JciEccsCollectionDetail getCollectionDetail() {
        return collectionDetail;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public HrmsPayrollCycle getCycle() {
        return cycle;
    }

    public JciEccsThriftTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public JciEccsThriftTransactionSource getSource() {
        return source;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
