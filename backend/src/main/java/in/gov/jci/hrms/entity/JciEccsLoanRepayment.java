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
 * One posted loan repayment - PAYROLL rows trace back to the {@link JciEccsCollectionDetail} that
 * produced them via collection_detail_id; CASH/ADJUSTMENT rows do not. total_amount is a DB GENERATED
 * column (principal + interest). Binds to the already-live jcieccs_loan_repayment (V87).
 */
@Entity
@Table(name = "jcieccs_loan_repayment")
public class JciEccsLoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private JciEccsLoan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private JciEccsLoanSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_detail_id")
    private JciEccsCollectionDetail collectionDetail;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "repayment_date", nullable = false)
    private LocalDate repaymentDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private HrmsPayrollCycle cycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private JciEccsRepaymentSource source;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "principal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal principalAmount = BigDecimal.ZERO;

    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_id")
    private JciEccsRecovery recovery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_allocation_id")
    private JciEccsRecoveryAllocation recoveryAllocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private JciEccsLoanRepayment reversalOf;

    protected JciEccsLoanRepayment() {
    }

    public JciEccsLoanRepayment(JciEccsLoan loan, JciEccsLoanSchedule schedule, JciEccsCollectionDetail collectionDetail,
                                 Long employeeId, LocalDate repaymentDate, HrmsPayrollCycle cycle, JciEccsRepaymentSource source,
                                 String referenceId, BigDecimal principalAmount, BigDecimal interestAmount, String remarks) {
        this.loan = loan;
        this.schedule = schedule;
        this.collectionDetail = collectionDetail;
        this.employeeId = employeeId;
        this.repaymentDate = repaymentDate;
        this.cycle = cycle;
        this.source = source;
        this.referenceId = referenceId;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public JciEccsLoan getLoan() {
        return loan;
    }

    public JciEccsLoanSchedule getSchedule() {
        return schedule;
    }

    public JciEccsCollectionDetail getCollectionDetail() {
        return collectionDetail;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public LocalDate getRepaymentDate() {
        return repaymentDate;
    }

    public HrmsPayrollCycle getCycle() {
        return cycle;
    }

    public JciEccsRepaymentSource getSource() {
        return source;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public JciEccsRecovery getRecovery() {
        return recovery;
    }

    public JciEccsRecoveryAllocation getRecoveryAllocation() {
        return recoveryAllocation;
    }

    public JciEccsLoanRepayment getReversalOf() {
        return reversalOf;
    }

    /** Additive setter (Phase 1) - JciEccsRecoveryPostingService links every new ledger row back to the
     * recovery/allocation that produced it; reversal rows also set reversalOf to the original ledger row
     * they compensate. Never touched by the pre-Phase-1 constructor/call sites. */
    public void linkRecovery(JciEccsRecovery recovery, JciEccsRecoveryAllocation recoveryAllocation, JciEccsLoanRepayment reversalOf) {
        this.recovery = recovery;
        this.recoveryAllocation = recoveryAllocation;
        this.reversalOf = reversalOf;
    }
}
