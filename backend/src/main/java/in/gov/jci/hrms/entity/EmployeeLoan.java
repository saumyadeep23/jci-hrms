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
 * loan_account_number uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration. interest_rate is
 * captured from the LoanType at sanction time and stays fixed thereafter,
 * even if the LoanType's own rate changes later.
 */
@Entity
@Table(name = "employee_loans")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeLoan implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_type_id", nullable = false)
    private LoanType loanType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "loan_account_number", nullable = false, length = 50)
    private String loanAccountNumber;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "total_installments", nullable = false)
    private Integer totalInstallments;

    @Column(name = "remaining_installments", nullable = false)
    private Integer remainingInstallments;

    @Column(name = "outstanding_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private LoanStatus status = LoanStatus.SANCTIONED;

    @Column(name = "sanction_date", nullable = false)
    private LocalDate sanctionDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeLoan() {
    }

    public EmployeeLoan(LoanType loanType, Employee employee, String loanAccountNumber, BigDecimal principalAmount,
                         BigDecimal interestRate, Integer totalInstallments, LocalDate sanctionDate) {
        this.loanType = loanType;
        this.employee = employee;
        this.loanAccountNumber = loanAccountNumber;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.totalInstallments = totalInstallments;
        this.remainingInstallments = totalInstallments;
        this.outstandingPrincipal = principalAmount;
        this.sanctionDate = sanctionDate;
    }

    public Long getId() {
        return id;
    }

    public LoanType getLoanType() {
        return loanType;
    }

    public Employee getEmployee() {
        return employee;
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

    public Integer getTotalInstallments() {
        return totalInstallments;
    }

    public Integer getRemainingInstallments() {
        return remainingInstallments;
    }

    public void setRemainingInstallments(Integer remainingInstallments) {
        this.remainingInstallments = remainingInstallments;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) {
        this.outstandingPrincipal = outstandingPrincipal;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
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
        return "EmployeeLoan";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("loanTypeId", loanType != null ? loanType.getId() : null);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("loanAccountNumber", loanAccountNumber);
        snapshot.put("principalAmount", principalAmount);
        snapshot.put("interestRate", interestRate);
        snapshot.put("totalInstallments", totalInstallments);
        snapshot.put("remainingInstallments", remainingInstallments);
        snapshot.put("outstandingPrincipal", outstandingPrincipal);
        snapshot.put("status", status);
        snapshot.put("sanctionDate", sanctionDate);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
