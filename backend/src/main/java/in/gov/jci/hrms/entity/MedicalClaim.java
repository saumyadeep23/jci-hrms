package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * claim_number uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration. total_allowed_amount
 * stays null until MedicalClaimService.verifyByHr() sets it.
 */
@Entity
@Table(name = "medical_claims")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class MedicalClaim implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, length = 50)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependent_id")
    private EmployeeDependent dependent;

    @Column(name = "total_claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalClaimedAmount = BigDecimal.ZERO;

    @Column(name = "total_allowed_amount", precision = 12, scale = 2)
    private BigDecimal totalAllowedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReimbursementClaimStatus status = ReimbursementClaimStatus.DRAFT;

    @Column(name = "submission_date")
    private LocalDate submissionDate;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "approved_by_finance", length = 150)
    private String approvedByFinance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected MedicalClaim() {
    }

    public MedicalClaim(String claimNumber, Employee employee, EmployeeDependent dependent) {
        this.claimNumber = claimNumber;
        this.employee = employee;
        this.dependent = dependent;
    }

    public Long getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeDependent getDependent() {
        return dependent;
    }

    public BigDecimal getTotalClaimedAmount() {
        return totalClaimedAmount;
    }

    public void setTotalClaimedAmount(BigDecimal totalClaimedAmount) {
        this.totalClaimedAmount = totalClaimedAmount;
    }

    public BigDecimal getTotalAllowedAmount() {
        return totalAllowedAmount;
    }

    public void setTotalAllowedAmount(BigDecimal totalAllowedAmount) {
        this.totalAllowedAmount = totalAllowedAmount;
    }

    public ReimbursementClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ReimbursementClaimStatus status) {
        this.status = status;
    }

    public LocalDate getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(LocalDate submissionDate) {
        this.submissionDate = submissionDate;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getApprovedByFinance() {
        return approvedByFinance;
    }

    public void setApprovedByFinance(String approvedByFinance) {
        this.approvedByFinance = approvedByFinance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "MedicalClaim";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("claimNumber", claimNumber);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("dependentId", dependent != null ? dependent.getId() : null);
        snapshot.put("totalClaimedAmount", totalClaimedAmount);
        snapshot.put("totalAllowedAmount", totalAllowedAmount);
        snapshot.put("status", status);
        snapshot.put("submissionDate", submissionDate);
        snapshot.put("verifiedBy", verifiedBy);
        snapshot.put("approvedByFinance", approvedByFinance);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
