package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Historical Migration Service staging row (spec section 4.6) - membership codes plus audited FY 2023-24
 * opening balances, staged raw then validated/promoted into a real {@link JciEccsMember}. Mirrors
 * StagingLegacyLoan's own shape/workflow (status reuses the same generic {@link StagingRowStatus}).
 * Binds to the already-created jcieccs_staging_member (V88).
 */
@Entity
@Table(name = "jcieccs_staging_member")
public class JciEccsStagingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "membership_code", nullable = false, length = 50)
    private String membershipCode;

    @Column(name = "membership_date", nullable = false)
    private LocalDate membershipDate;

    @Column(name = "share_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal shareBalance = BigDecimal.ZERO;

    @Column(name = "fund_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal fundBalance = BigDecimal.ZERO;

    @Column(name = "security_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal securityBalance = BigDecimal.ZERO;

    @Column(name = "thrift_monthly_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal thriftMonthlyAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JciEccsStagingMember() {
    }

    public JciEccsStagingMember(String employeeCode, String membershipCode, LocalDate membershipDate, BigDecimal shareBalance,
                                 BigDecimal fundBalance, BigDecimal securityBalance, BigDecimal thriftMonthlyAmount) {
        this.employeeCode = employeeCode;
        this.membershipCode = membershipCode;
        this.membershipDate = membershipDate;
        this.shareBalance = shareBalance;
        this.fundBalance = fundBalance;
        this.securityBalance = securityBalance;
        this.thriftMonthlyAmount = thriftMonthlyAmount;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getMembershipCode() {
        return membershipCode;
    }

    public LocalDate getMembershipDate() {
        return membershipDate;
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

    public BigDecimal getThriftMonthlyAmount() {
        return thriftMonthlyAmount;
    }

    public StagingRowStatus getStatus() {
        return status;
    }

    public void setStatus(StagingRowStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
