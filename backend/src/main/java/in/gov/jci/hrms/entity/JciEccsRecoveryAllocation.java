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
 * JCIECCS Lifecycle Engine Phase 1 - one component-level split of a {@link JciEccsRecovery} (Thrift, Term
 * Interest, Term Principal, Emergency Interest, Emergency Principal). {@code expectedAmount} is what the
 * demand/schedule said was due for this component; {@code allocatedAmount} is what this recovery actually
 * covers of it, per the module's own Thrift -&gt; Term Interest -&gt; Emergency Interest -&gt; Term Principal
 * -&gt; Emergency Principal priority cascade ({@code allocationSequence}, 1-based) - see
 * JciEccsRecoveryAllocationService. Binds to jcieccs_recovery_allocation (V90).
 */
@Entity
@Table(name = "jcieccs_recovery_allocation")
public class JciEccsRecoveryAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_id", nullable = false)
    private JciEccsRecovery recovery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_detail_id")
    private JciEccsCollectionDetail collectionDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private JciEccsLoan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_schedule_id")
    private JciEccsLoanSchedule loanSchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 30)
    private JciEccsRecoveryComponent component;

    @Column(name = "expected_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedAmount = BigDecimal.ZERO;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(name = "allocation_sequence", nullable = false)
    private int allocationSequence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JciEccsRecoveryAllocation() {
    }

    public JciEccsRecoveryAllocation(JciEccsRecovery recovery, JciEccsCollectionDetail collectionDetail, JciEccsLoan loan,
                                      JciEccsLoanSchedule loanSchedule, JciEccsRecoveryComponent component,
                                      BigDecimal expectedAmount, BigDecimal allocatedAmount, int allocationSequence) {
        this.recovery = recovery;
        this.collectionDetail = collectionDetail;
        this.loan = loan;
        this.loanSchedule = loanSchedule;
        this.component = component;
        this.expectedAmount = expectedAmount;
        this.allocatedAmount = allocatedAmount;
        this.allocationSequence = allocationSequence;
    }

    public Long getId() {
        return id;
    }

    public JciEccsRecovery getRecovery() {
        return recovery;
    }

    public JciEccsCollectionDetail getCollectionDetail() {
        return collectionDetail;
    }

    public JciEccsLoan getLoan() {
        return loan;
    }

    public JciEccsLoanSchedule getLoanSchedule() {
        return loanSchedule;
    }

    public void setLoanSchedule(JciEccsLoanSchedule loanSchedule) {
        this.loanSchedule = loanSchedule;
    }

    public JciEccsRecoveryComponent getComponent() {
        return component;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public int getAllocationSequence() {
        return allocationSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
