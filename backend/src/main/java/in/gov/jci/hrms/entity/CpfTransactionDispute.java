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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An employee's dispute against one immutable {@link CpfTrustMemberLedgerEntry} - maps onto
 * cpf_transaction_disputes (V83). This entity is a pure OBSERVER of the ledger: it holds a foreign key to
 * the disputed row's id and nothing else accounting-shaped (no amount, no balance, no contribution figure)
 * - there is structurally no column here through which any dispute-lifecycle action could alter ledger
 * data, satisfying the module's own non-negotiable rule (raising, reviewing, resolving or rejecting a
 * dispute never touches {@code cpfLedgerTransaction}'s own fields).
 *
 * <p>{@code @Version} gives optimistic-locking concurrency protection (Part 29) - new to this codebase
 * (no other entity here uses it yet), added because two reviewers racing to resolve/reject the same
 * dispute is a realistic scenario this module specifically needs to guard against, unlike this app's other
 * single-actor workflows.
 */
@Entity
@Table(name = "cpf_transaction_disputes")
@EntityListeners(AuditableEntityListener.class)
public class CpfTransactionDispute implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispute_number", nullable = false, unique = true, length = 30)
    private String disputeNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cpf_ledger_transaction_id", nullable = false)
    private CpfTrustMemberLedgerEntry cpfLedgerTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_category", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private CpfDisputeCategory disputeCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private CpfDisputeStatus status = CpfDisputeStatus.OPEN;

    @Column(name = "employee_remarks", nullable = false, columnDefinition = "TEXT")
    private String employeeRemarks;

    @Column(name = "attachment_s3_key", length = 500)
    private String attachmentS3Key;

    @Column(name = "attachment_original_filename", length = 255)
    private String attachmentOriginalFilename;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_by", nullable = false)
    private Employee raisedBy;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private Employee assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "reviewer_remarks", columnDefinition = "TEXT")
    private String reviewerRemarks;

    @Column(name = "clarification_request", columnDefinition = "TEXT")
    private String clarificationRequest;

    @Column(name = "employee_response", columnDefinition = "TEXT")
    private String employeeResponse;

    @Column(name = "resolution_remarks", columnDefinition = "TEXT")
    private String resolutionRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private Employee resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private Employee rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CpfTransactionDispute() {
    }

    public CpfTransactionDispute(String disputeNumber, Employee employee, CpfTrustMemberLedgerEntry cpfLedgerTransaction,
                                  CpfDisputeCategory disputeCategory, String employeeRemarks, Employee raisedBy) {
        this.disputeNumber = disputeNumber;
        this.employee = employee;
        this.cpfLedgerTransaction = cpfLedgerTransaction;
        this.disputeCategory = disputeCategory;
        this.employeeRemarks = employeeRemarks;
        this.raisedBy = raisedBy;
        this.raisedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDisputeNumber() {
        return disputeNumber;
    }

    public Employee getEmployee() {
        return employee;
    }

    public CpfTrustMemberLedgerEntry getCpfLedgerTransaction() {
        return cpfLedgerTransaction;
    }

    public CpfDisputeCategory getDisputeCategory() {
        return disputeCategory;
    }

    public CpfDisputeStatus getStatus() {
        return status;
    }

    public void setStatus(CpfDisputeStatus status) {
        this.status = status;
    }

    public String getEmployeeRemarks() {
        return employeeRemarks;
    }

    public String getAttachmentS3Key() {
        return attachmentS3Key;
    }

    public void setAttachmentS3Key(String attachmentS3Key) {
        this.attachmentS3Key = attachmentS3Key;
    }

    public String getAttachmentOriginalFilename() {
        return attachmentOriginalFilename;
    }

    public void setAttachmentOriginalFilename(String attachmentOriginalFilename) {
        this.attachmentOriginalFilename = attachmentOriginalFilename;
    }

    public Employee getRaisedBy() {
        return raisedBy;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Employee getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Employee assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getReviewerRemarks() {
        return reviewerRemarks;
    }

    public void setReviewerRemarks(String reviewerRemarks) {
        this.reviewerRemarks = reviewerRemarks;
    }

    public String getClarificationRequest() {
        return clarificationRequest;
    }

    public void setClarificationRequest(String clarificationRequest) {
        this.clarificationRequest = clarificationRequest;
    }

    public String getEmployeeResponse() {
        return employeeResponse;
    }

    public void setEmployeeResponse(String employeeResponse) {
        this.employeeResponse = employeeResponse;
    }

    public String getResolutionRemarks() {
        return resolutionRemarks;
    }

    public void setResolutionRemarks(String resolutionRemarks) {
        this.resolutionRemarks = resolutionRemarks;
    }

    public Employee getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Employee resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Employee getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(Employee rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(Instant withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String auditEntityName() {
        return "CpfTransactionDispute";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("disputeNumber", disputeNumber);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("cpfLedgerTransactionId", cpfLedgerTransaction != null ? cpfLedgerTransaction.getId() : null);
        snapshot.put("disputeCategory", disputeCategory);
        snapshot.put("status", status);
        snapshot.put("assignedToEmployeeId", assignedTo != null ? assignedTo.getId() : null);
        snapshot.put("resolvedByEmployeeId", resolvedBy != null ? resolvedBy.getId() : null);
        snapshot.put("rejectedByEmployeeId", rejectedBy != null ? rejectedBy.getId() : null);
        return snapshot;
    }
}
