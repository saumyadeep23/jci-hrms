package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Withdrawal Type Master (Part 5) - binds to the already-live cpf_withdrawal_type_master (3 seeded rows: NON_REFUNDABLE/REFUNDABLE/FINAL_SETTLEMENT). See CpfHeadMaster's own javadoc for why this uses a UUID id. */
@Entity
@Table(name = "cpf_withdrawal_type_master")
public class CpfWithdrawalTypeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "is_refundable", nullable = false)
    private boolean refundable;

    @Column(name = "is_settlement", nullable = false)
    private boolean settlement;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfWithdrawalTypeMaster() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isRefundable() {
        return refundable;
    }

    public boolean isSettlement() {
        return settlement;
    }

    public boolean isActive() {
        return active;
    }
}
