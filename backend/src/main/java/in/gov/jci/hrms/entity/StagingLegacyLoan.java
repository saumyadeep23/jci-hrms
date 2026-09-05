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

@Entity
@Table(name = "staging_legacy_loans")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacyLoan implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "loan_type_code", nullable = false, length = 20)
    private String loanTypeCode;

    @Column(name = "loan_account_number", nullable = false, length = 50)
    private String loanAccountNumber;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "sanction_date", nullable = false)
    private LocalDate sanctionDate;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "outstanding_principal", precision = 12, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "remaining_installments")
    private Integer remainingInstallments;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_status", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private LoanStatus loanStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacyLoan() {
    }

    public StagingLegacyLoan(String employeeCode, String loanTypeCode, String loanAccountNumber, BigDecimal principalAmount,
                              BigDecimal interestRate, LocalDate sanctionDate, Integer tenureMonths, LoanStatus loanStatus) {
        this.employeeCode = employeeCode;
        this.loanTypeCode = loanTypeCode;
        this.loanAccountNumber = loanAccountNumber;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.sanctionDate = sanctionDate;
        this.tenureMonths = tenureMonths;
        this.loanStatus = loanStatus;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getLoanTypeCode() {
        return loanTypeCode;
    }

    public String getLoanAccountNumber() {
        return loanAccountNumber;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public void setDisbursementDate(LocalDate disbursementDate) {
        this.disbursementDate = disbursementDate;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) {
        this.outstandingPrincipal = outstandingPrincipal;
    }

    public Integer getRemainingInstallments() {
        return remainingInstallments;
    }

    public void setRemainingInstallments(Integer remainingInstallments) {
        this.remainingInstallments = remainingInstallments;
    }

    public LoanStatus getLoanStatus() {
        return loanStatus;
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
        return "StagingLegacyLoan";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("loanAccountNumber", loanAccountNumber);
        snapshot.put("loanStatus", loanStatus);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
