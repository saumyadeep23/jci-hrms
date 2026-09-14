package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per JCIECCS member's CURRENT financial position (employee_id/membership_code are
 * blanket-unique - a true 1:1 with Employee, mutated in place as contributions/deposits post, not a
 * per-change history table). effective_from/effective_to bracket active society membership itself (e.g.
 * resignation sets effective_to); {@code version} is a plain optimistic-lock counter, unrelated to any
 * revision history. Binds to the already-live jcieccs_member (V87).
 */
@Entity
@Table(name = "jcieccs_member")
public class JciEccsMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "membership_code", nullable = false, length = 50)
    private String membershipCode;

    @Column(name = "membership_date", nullable = false)
    private LocalDate membershipDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 20)
    private JciEccsMembershipStatus membershipStatus = JciEccsMembershipStatus.ACTIVE;

    @Column(name = "share_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal shareBalance = BigDecimal.ZERO;

    @Column(name = "fund_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal fundBalance = BigDecimal.ZERO;

    @Column(name = "security_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal securityBalance = BigDecimal.ZERO;

    @Column(name = "thrift_monthly_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal thriftMonthlyAmount = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected JciEccsMember() {
    }

    public JciEccsMember(Long employeeId, String membershipCode, LocalDate membershipDate, LocalDate effectiveFrom) {
        this.employeeId = employeeId;
        this.membershipCode = membershipCode;
        this.membershipDate = membershipDate;
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getMembershipCode() {
        return membershipCode;
    }

    public LocalDate getMembershipDate() {
        return membershipDate;
    }

    public JciEccsMembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(JciEccsMembershipStatus membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public BigDecimal getShareBalance() {
        return shareBalance;
    }

    public void setShareBalance(BigDecimal shareBalance) {
        this.shareBalance = shareBalance;
    }

    public BigDecimal getFundBalance() {
        return fundBalance;
    }

    public void setFundBalance(BigDecimal fundBalance) {
        this.fundBalance = fundBalance;
    }

    public BigDecimal getSecurityBalance() {
        return securityBalance;
    }

    public void setSecurityBalance(BigDecimal securityBalance) {
        this.securityBalance = securityBalance;
    }

    public BigDecimal getThriftMonthlyAmount() {
        return thriftMonthlyAmount;
    }

    public void setThriftMonthlyAmount(BigDecimal thriftMonthlyAmount) {
        this.thriftMonthlyAmount = thriftMonthlyAmount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
