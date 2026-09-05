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
 * (WHERE deleted_at IS NULL) in the Flyway migration. out_of_pocket_allowed
 * stays null until TadaClaimService.verifyByHr() sets it.
 */
@Entity
@Table(name = "tada_claims")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class TadaClaim implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_request_id", nullable = false)
    private TourRequest tourRequest;

    @Column(name = "claim_number", nullable = false, length = 50)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "out_of_pocket_claimed", nullable = false, precision = 12, scale = 2)
    private BigDecimal outOfPocketClaimed;

    @Column(name = "out_of_pocket_allowed", precision = 12, scale = 2)
    private BigDecimal outOfPocketAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReimbursementClaimStatus status = ReimbursementClaimStatus.DRAFT;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "approved_by_finance", length = 150)
    private String approvedByFinance;

    @Column(name = "submission_date")
    private LocalDate submissionDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TadaClaim() {
    }

    public TadaClaim(TourRequest tourRequest, String claimNumber, Employee employee, BigDecimal outOfPocketClaimed) {
        this.tourRequest = tourRequest;
        this.claimNumber = claimNumber;
        this.employee = employee;
        this.outOfPocketClaimed = outOfPocketClaimed;
    }

    public Long getId() {
        return id;
    }

    public TourRequest getTourRequest() {
        return tourRequest;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Employee getEmployee() {
        return employee;
    }

    public BigDecimal getOutOfPocketClaimed() {
        return outOfPocketClaimed;
    }

    public BigDecimal getOutOfPocketAllowed() {
        return outOfPocketAllowed;
    }

    public void setOutOfPocketAllowed(BigDecimal outOfPocketAllowed) {
        this.outOfPocketAllowed = outOfPocketAllowed;
    }

    public ReimbursementClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ReimbursementClaimStatus status) {
        this.status = status;
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

    public LocalDate getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(LocalDate submissionDate) {
        this.submissionDate = submissionDate;
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
        return "TadaClaim";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tourRequestId", tourRequest != null ? tourRequest.getId() : null);
        snapshot.put("claimNumber", claimNumber);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("outOfPocketClaimed", outOfPocketClaimed);
        snapshot.put("outOfPocketAllowed", outOfPocketAllowed);
        snapshot.put("status", status);
        snapshot.put("verifiedBy", verifiedBy);
        snapshot.put("approvedByFinance", approvedByFinance);
        snapshot.put("submissionDate", submissionDate);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
