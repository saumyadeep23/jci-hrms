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
 * cpf_loan_batch_recovery (V84) - one immutable row per (loan, payroll batch, recovery phase) actually
 * resolved by {@code CpfLedgerSyncService}. Append-only, like {@link CpfTrustMemberLedgerEntry} itself
 * (no {@code Auditable} before/after snapshot - there is nothing to snapshot a change against), and
 * its own unique constraint IS the idempotency guard for Part 24: a second attempt to sync the same
 * batch's recovery for the same loan/phase is a no-op, not a second ledger posting.
 */
@Entity
@Table(name = "cpf_loan_batch_recovery")
public class CpfLoanBatchRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private CpfLoanApplication loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_batch_id", nullable = false)
    private PayrollBatch payrollBatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_phase", nullable = false, length = 20)
    private CpfLoanRecoveryPhase recoveryPhase;

    @Column(name = "amount_recovered", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRecovered;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private CpfLoanBatchRecoveryOutcome outcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_entry_id")
    private CpfTrustMemberLedgerEntry ledgerEntry;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfLoanBatchRecovery() {
    }

    public CpfLoanBatchRecovery(CpfLoanApplication loan, PayrollBatch payrollBatch, CpfLoanRecoveryPhase recoveryPhase,
                                 BigDecimal amountRecovered, CpfLoanBatchRecoveryOutcome outcome, CpfTrustMemberLedgerEntry ledgerEntry) {
        this.loan = loan;
        this.payrollBatch = payrollBatch;
        this.recoveryPhase = recoveryPhase;
        this.amountRecovered = amountRecovered;
        this.outcome = outcome;
        this.ledgerEntry = ledgerEntry;
    }

    public Long getId() {
        return id;
    }

    public CpfLoanApplication getLoan() {
        return loan;
    }

    public PayrollBatch getPayrollBatch() {
        return payrollBatch;
    }

    public CpfLoanRecoveryPhase getRecoveryPhase() {
        return recoveryPhase;
    }

    public BigDecimal getAmountRecovered() {
        return amountRecovered;
    }

    public CpfLoanBatchRecoveryOutcome getOutcome() {
        return outcome;
    }

    public CpfTrustMemberLedgerEntry getLedgerEntry() {
        return ledgerEntry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
