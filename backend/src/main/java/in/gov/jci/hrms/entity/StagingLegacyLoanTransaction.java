package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.util.LinkedHashMap;
import java.util.Map;

/** staging_loan_id is a plain FK to the staging row, not a JPA association - orphan validity (FR-MIG.4) is resolved in service logic against the parent's own validation outcome. */
@Entity
@Table(name = "staging_legacy_loan_transactions")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacyLoanTransaction implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staging_loan_id", nullable = false)
    private Long stagingLoanId;

    @Column(name = "loan_account_number", nullable = false, length = 50)
    private String loanAccountNumber;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "principal_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalComponent;

    @Column(name = "interest_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestComponent;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacyLoanTransaction() {
    }

    public StagingLegacyLoanTransaction(Long stagingLoanId, String loanAccountNumber, LocalDate paymentDate,
                                         BigDecimal principalComponent, BigDecimal interestComponent, BigDecimal totalAmount) {
        this.stagingLoanId = stagingLoanId;
        this.loanAccountNumber = loanAccountNumber;
        this.paymentDate = paymentDate;
        this.principalComponent = principalComponent;
        this.interestComponent = interestComponent;
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getStagingLoanId() {
        return stagingLoanId;
    }

    public String getLoanAccountNumber() {
        return loanAccountNumber;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getPrincipalComponent() {
        return principalComponent;
    }

    public BigDecimal getInterestComponent() {
        return interestComponent;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
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

    @Override
    public String auditEntityName() {
        return "StagingLegacyLoanTransaction";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stagingLoanId", stagingLoanId);
        snapshot.put("loanAccountNumber", loanAccountNumber);
        snapshot.put("paymentDate", paymentDate);
        snapshot.put("totalAmount", totalAmount);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
