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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCD Type-2 banking ledger - PIMS_SPEC.md Step 3. Inserting a new row with
 * status=ACTIVE/isPrimaryDisbursal=true fires the DB trigger
 * fn_sync_employee_bank_change, which archives every other ACTIVE row for
 * the employee as HISTORICAL and refreshes employees.bank_* - see V30
 * migration. Application code never flips an old row to HISTORICAL itself.
 */
@Entity
@Table(name = "employee_bank_accounts")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeBankAccount implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "bank_branch", nullable = false, length = 150)
    private String bankBranch;

    @Column(name = "bank_account_number", nullable = false, length = 35)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", nullable = false, length = 11)
    private String bankIfsc;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private BankAccountType accountType = BankAccountType.SALARY;

    @Column(name = "is_primary_disbursal", nullable = false)
    private boolean primaryDisbursal = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private BankAccountStatus status = BankAccountStatus.ACTIVE;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "cancelled_cheque_s3_key", length = 500)
    private String cancelledChequeS3Key;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeBankAccount() {
    }

    public EmployeeBankAccount(Employee employee, String bankName, String bankBranch, String bankAccountNumber, String bankIfsc) {
        this.employee = employee;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.bankAccountNumber = bankAccountNumber;
        this.bankIfsc = bankIfsc;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public void setBankBranch(String bankBranch) {
        this.bankBranch = bankBranch;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankIfsc() {
        return bankIfsc;
    }

    public void setBankIfsc(String bankIfsc) {
        this.bankIfsc = bankIfsc;
    }

    public BankAccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(BankAccountType accountType) {
        this.accountType = accountType;
    }

    public boolean isPrimaryDisbursal() {
        return primaryDisbursal;
    }

    public void setPrimaryDisbursal(boolean primaryDisbursal) {
        this.primaryDisbursal = primaryDisbursal;
    }

    public BankAccountStatus getStatus() {
        return status;
    }

    public void setStatus(BankAccountStatus status) {
        this.status = status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getCancelledChequeS3Key() {
        return cancelledChequeS3Key;
    }

    public void setCancelledChequeS3Key(String cancelledChequeS3Key) {
        this.cancelledChequeS3Key = cancelledChequeS3Key;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeBankAccount";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("bankName", bankName);
        snapshot.put("bankIfsc", bankIfsc);
        snapshot.put("accountType", accountType);
        snapshot.put("isPrimaryDisbursal", primaryDisbursal);
        snapshot.put("status", status);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
