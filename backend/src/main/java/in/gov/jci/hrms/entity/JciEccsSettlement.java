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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JCIECCS Lifecycle Engine Phase 3 - the detailed no-dues calculation snapshot for one member (spec
 * section 30/36/37). One row per member (unique constraint), refreshed - never duplicated - on each
 * recalculation. This is NOT a second settlement status engine: the authoritative HOLD/CLEARED state
 * lives on the linked {@link ExitClearanceItem} (department JCIECCS), reusing the existing HR exit
 * clearance workflow exactly as every other department already does. {@code setoffAmount} is always
 * zero - no approved JCIECCS set-off rule exists in this codebase, and none is invented here (see
 * {@link in.gov.jci.hrms.service.JciEccsNoDuesService}'s own javadoc). Binds to jcieccs_settlement (V92).
 */
@Entity
@Table(name = "jcieccs_settlement")
public class JciEccsSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_clearance_request_id")
    private ExitClearanceRequest exitClearanceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_clearance_item_id")
    private ExitClearanceItem exitClearanceItem;

    @Column(name = "separation_type", length = 50)
    private String separationType;

    @Column(name = "separation_date")
    private LocalDate separationDate;

    @Column(name = "share_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal shareBalance = BigDecimal.ZERO;

    @Column(name = "fund_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal fundBalance = BigDecimal.ZERO;

    @Column(name = "security_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal securityBalance = BigDecimal.ZERO;

    @Column(name = "thrift_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal thriftBalance = BigDecimal.ZERO;

    @Column(name = "term_principal_outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal termPrincipalOutstanding = BigDecimal.ZERO;

    @Column(name = "term_interest_outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal termInterestOutstanding = BigDecimal.ZERO;

    @Column(name = "emergency_principal_outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal emergencyPrincipalOutstanding = BigDecimal.ZERO;

    @Column(name = "emergency_interest_outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal emergencyInterestOutstanding = BigDecimal.ZERO;

    @Column(name = "other_dues", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherDues = BigDecimal.ZERO;

    @Column(name = "setoff_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal setoffAmount = BigDecimal.ZERO;

    @Column(name = "net_liability", nullable = false, precision = 14, scale = 2)
    private BigDecimal netLiability = BigDecimal.ZERO;

    @Column(name = "stale", nullable = false)
    private boolean stale = false;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "calculated_by")
    private Long calculatedBy;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JciEccsSettlement() {
    }

    public JciEccsSettlement(JciEccsMember member, Long employeeId) {
        this.member = member;
        this.employeeId = employeeId;
    }

    /** Recomputes every figure in place (the upsert half of "one row per member, refreshed not
     * duplicated"). Never touches exitClearanceItem/exitClearanceRequest linkage or stale on its own -
     * the service decides those based on whether the item is already CLEARED. */
    public void refresh(BigDecimal shareBalance, BigDecimal fundBalance, BigDecimal securityBalance, BigDecimal thriftBalance,
                         BigDecimal termPrincipalOutstanding, BigDecimal termInterestOutstanding, BigDecimal emergencyPrincipalOutstanding,
                         BigDecimal emergencyInterestOutstanding, BigDecimal otherDues, BigDecimal netLiability, Long calculatedBy) {
        this.shareBalance = shareBalance;
        this.fundBalance = fundBalance;
        this.securityBalance = securityBalance;
        this.thriftBalance = thriftBalance;
        this.termPrincipalOutstanding = termPrincipalOutstanding;
        this.termInterestOutstanding = termInterestOutstanding;
        this.emergencyPrincipalOutstanding = emergencyPrincipalOutstanding;
        this.emergencyInterestOutstanding = emergencyInterestOutstanding;
        this.otherDues = otherDues;
        this.netLiability = netLiability;
        this.calculatedAt = Instant.now();
        this.calculatedBy = calculatedBy;
    }

    public void linkExitClearance(ExitClearanceRequest request, ExitClearanceItem item, String separationType, LocalDate separationDate) {
        this.exitClearanceRequest = request;
        this.exitClearanceItem = item;
        this.separationType = separationType;
        this.separationDate = separationDate;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public void setRemarks(String remarks) {
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

    public ExitClearanceRequest getExitClearanceRequest() {
        return exitClearanceRequest;
    }

    public ExitClearanceItem getExitClearanceItem() {
        return exitClearanceItem;
    }

    public String getSeparationType() {
        return separationType;
    }

    public LocalDate getSeparationDate() {
        return separationDate;
    }

    public BigDecimal getShareBalance() {
        return shareBalance;
    }

    public BigDecimal getFundBalance() {
        return fundBalance;
    }

    public BigDecimal getSecurityBalance() {
        return securityBalance;
    }

    public BigDecimal getThriftBalance() {
        return thriftBalance;
    }

    public BigDecimal getTermPrincipalOutstanding() {
        return termPrincipalOutstanding;
    }

    public BigDecimal getTermInterestOutstanding() {
        return termInterestOutstanding;
    }

    public BigDecimal getEmergencyPrincipalOutstanding() {
        return emergencyPrincipalOutstanding;
    }

    public BigDecimal getEmergencyInterestOutstanding() {
        return emergencyInterestOutstanding;
    }

    public BigDecimal getOtherDues() {
        return otherDues;
    }

    public BigDecimal getSetoffAmount() {
        return setoffAmount;
    }

    public BigDecimal getNetLiability() {
        return netLiability;
    }

    public boolean isStale() {
        return stale;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public Long getCalculatedBy() {
        return calculatedBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
