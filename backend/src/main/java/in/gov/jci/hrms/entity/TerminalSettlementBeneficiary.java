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
 * Disbursement allocation row(s) for a TerminalSettlement - a single SELF
 * (100%) row for RETIRED/RESIGNED/VRS/TERMINATED, or one row per
 * NOMINEE/LEGAL_HEIR for DECEASED (share_percentage must sum to 100 and
 * allocated_amount to net_terminal_payable before approval - see
 * TerminalSettlementService.approve()). V58 migration.
 */
@Entity
@Table(name = "terminal_settlement_beneficiaries")
public class TerminalSettlementBeneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private TerminalSettlement settlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", nullable = false, length = 50)
    private BeneficiaryType beneficiaryType;

    @Column(name = "beneficiary_name", nullable = false, length = 150)
    private String beneficiaryName;

    @Column(name = "relationship", nullable = false, length = 50)
    private String relationship;

    @Column(name = "share_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePercentage;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;

    /** Nullable (V59): a DECEASED settlement's nominee rows are auto-populated from employee_nominees, which carries no bank details - HR fills these in before TerminalSettlementService.approve(), which enforces both are non-blank by then. */
    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "bank_ifsc", length = 20)
    private String bankIfsc;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TerminalSettlementBeneficiary() {
    }

    public TerminalSettlementBeneficiary(TerminalSettlement settlement, BeneficiaryType beneficiaryType, String beneficiaryName,
                                          String relationship, BigDecimal sharePercentage, BigDecimal allocatedAmount,
                                          String bankAccountNo, String bankIfsc, String bankName, String panNumber) {
        this.settlement = settlement;
        this.beneficiaryType = beneficiaryType;
        this.beneficiaryName = beneficiaryName;
        this.relationship = relationship;
        this.sharePercentage = sharePercentage;
        this.allocatedAmount = allocatedAmount;
        this.bankAccountNo = bankAccountNo;
        this.bankIfsc = bankIfsc;
        this.bankName = bankName;
        this.panNumber = panNumber;
    }

    public Long getId() {
        return id;
    }

    public TerminalSettlement getSettlement() {
        return settlement;
    }

    public BeneficiaryType getBeneficiaryType() {
        return beneficiaryType;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getRelationship() {
        return relationship;
    }

    public BigDecimal getSharePercentage() {
        return sharePercentage;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public String getBankAccountNo() {
        return bankAccountNo;
    }

    public String getBankIfsc() {
        return bankIfsc;
    }

    public String getBankName() {
        return bankName;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
