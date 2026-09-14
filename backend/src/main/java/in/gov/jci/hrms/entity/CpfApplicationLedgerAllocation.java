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

import java.math.BigDecimal;
import java.util.UUID;

/** One head's allocated/recovered amount for a {@link CpfApplication} - binds to the already-live cpf_application_ledger_allocation. Per-application, per-head breakdown; the actual balance movement is posted to cpf_trust_member_ledger_entries - see CpfApplication's own javadoc. */
@Entity
@Table(name = "cpf_application_ledger_allocation")
public class CpfApplicationLedgerAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CpfApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "head_id", nullable = false)
    private CpfHeadMaster head;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "recovered_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    protected CpfApplicationLedgerAllocation() {
    }

    public CpfApplicationLedgerAllocation(CpfApplication application, CpfHeadMaster head, BigDecimal allocatedAmount) {
        this.application = application;
        this.head = head;
        this.allocatedAmount = allocatedAmount;
    }

    public UUID getId() {
        return id;
    }

    public CpfApplication getApplication() {
        return application;
    }

    public CpfHeadMaster getHead() {
        return head;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public BigDecimal getRecoveredAmount() {
        return recoveredAmount;
    }

    public void setRecoveredAmount(BigDecimal recoveredAmount) {
        this.recoveredAmount = recoveredAmount;
    }
}
