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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A CPF Trust-administered loan/withdrawal against a member's own CPF balance - maps onto
 * cpf_loan_applications, a table already live on the shared dev database (see V74's own header comment).
 * Deliberately separate from the general-purpose employee_loans (HBA/Term/Emergency/Festival advances
 * etc., see LoanType/EmployeeLoan) since a Trust loan draws down the member's own Trust corpus under CPF
 * Trust Rules, not general company loan policy - CpfLedgerSyncService posts a LOAN_REPAYMENT
 * cpf_trust_member_ledger_entries row and reduces this row's own outstandingBalance/incrementss
 * recoveredInstallments directly from the payroll-extracted recovery amount (Head 30/51), not via
 * EmployeeLoan/LoanRepaymentService's own reducing-balance repayment split.
 */
@Entity
@Table(name = "cpf_loan_applications")
@EntityListeners(AuditableEntityListener.class)
public class CpfLoanApplication implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_application_no", nullable = false, unique = true, length = 50)
    private String loanApplicationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private CpfLoanType loanType;

    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose;

    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "sanctioned_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sanctionedAmount = BigDecimal.ZERO;

    @Column(name = "sanction_order_no", length = 100)
    private String sanctionOrderNo;

    @Column(name = "sanction_date")
    private LocalDate sanctionDate;

    /**
     * Head-wise split of a NON_REFUNDABLE_WITHDRAWAL's sanctionedAmount across the three funds it is drawn
     * from - must sum to sanctionedAmount, enforced in sanctionLoan(). Always 0 for a REFUNDABLE_LOAN,
     * which debits EE only (spilling into VPF if EE is insufficient) rather than an officer-chosen split.
     */
    @Column(name = "sanc_nrw_ee", nullable = false, precision = 12, scale = 2)
    private BigDecimal sancNrwEe = BigDecimal.ZERO;

    @Column(name = "sanc_nrw_er", nullable = false, precision = 12, scale = 2)
    private BigDecimal sancNrwEr = BigDecimal.ZERO;

    @Column(name = "sanc_nrw_vpf", nullable = false, precision = 12, scale = 2)
    private BigDecimal sancNrwVpf = BigDecimal.ZERO;

    @Column(name = "monthly_recovery_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRecoveryPrincipal = BigDecimal.ZERO;

    @Column(name = "monthly_recovery_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRecoveryInterest = BigDecimal.ZERO;

    @Column(name = "total_installments", nullable = false)
    private int totalInstallments = 0;

    @Column(name = "recovered_installments", nullable = false)
    private int recoveredInstallments = 0;

    @Column(name = "outstanding_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private CpfLoanApplicationStatus status = CpfLoanApplicationStatus.APPLIED;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "rejection_remarks", columnDefinition = "TEXT")
    private String rejectionRemarks;

    @Column(name = "application_reason", columnDefinition = "TEXT")
    private String applicationReason;

    @Column(name = "base_cpf_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal baseCpfRate = new BigDecimal("8.25");

    /** baseCpfRate + the notified loan markup (see CpfStatutoryInterestRate.loanMarkupRate) - resolved via CpfRateResolutionService at sanction time, not hardcoded here. */
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate = new BigDecimal("9.25");

    @Column(name = "total_interest_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalInterestAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingInterest = BigDecimal.ZERO;

    @Column(name = "total_interest_installments", nullable = false)
    private int totalInterestInstallments = 0;

    @Column(name = "recovered_interest_installments", nullable = false)
    private int recoveredInterestInstallments = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_phase", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private CpfLoanRecoveryPhase recoveryPhase = CpfLoanRecoveryPhase.PRINCIPAL;

    @Column(name = "is_preclosed", nullable = false)
    private boolean preclosed = false;

    @Column(name = "preclosed_at")
    private Instant preclosedAt;

    /**
     * cpf_application.id (V84) - set only when this loan was created by
     * {@code CpfApplicationService.disburse()} bridging a refundable rule-engine withdrawal into this
     * table's own repayment/recovery/settlement machinery (Part 4/7). Null for a loan applied directly
     * through {@link in.gov.jci.hrms.service.CpfLoanApplicationService}'s own legacy apply/sanction
     * flow. A plain scalar UUID (not a JPA @ManyToOne to CpfApplication) - same lighter-weight
     * cross-aggregate reference style {@code CpfApplication.employeeCode} itself already uses, rather
     * than forcing a bidirectional object-graph coupling between the two independently-lifecycled
     * aggregates.
     */
    @Column(name = "cpf_application_id")
    private java.util.UUID cpfApplicationId;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CpfLoanApplication() {
    }

    public CpfLoanApplication(String loanApplicationNo, Employee employee, CpfLoanType loanType, String purpose, BigDecimal appliedAmount) {
        this.loanApplicationNo = loanApplicationNo;
        this.employee = employee;
        this.loanType = loanType;
        this.purpose = purpose;
        this.appliedAmount = appliedAmount;
    }

    public Long getId() {
        return id;
    }

    public String getLoanApplicationNo() {
        return loanApplicationNo;
    }

    public Employee getEmployee() {
        return employee;
    }

    public CpfLoanType getLoanType() {
        return loanType;
    }

    public String getPurpose() {
        return purpose;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        this.sanctionedAmount = sanctionedAmount;
    }

    public String getSanctionOrderNo() {
        return sanctionOrderNo;
    }

    public void setSanctionOrderNo(String sanctionOrderNo) {
        this.sanctionOrderNo = sanctionOrderNo;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public void setSanctionDate(LocalDate sanctionDate) {
        this.sanctionDate = sanctionDate;
    }

    public BigDecimal getSancNrwEe() {
        return sancNrwEe;
    }

    public void setSancNrwEe(BigDecimal sancNrwEe) {
        this.sancNrwEe = sancNrwEe;
    }

    public BigDecimal getSancNrwEr() {
        return sancNrwEr;
    }

    public void setSancNrwEr(BigDecimal sancNrwEr) {
        this.sancNrwEr = sancNrwEr;
    }

    public BigDecimal getSancNrwVpf() {
        return sancNrwVpf;
    }

    public void setSancNrwVpf(BigDecimal sancNrwVpf) {
        this.sancNrwVpf = sancNrwVpf;
    }

    public BigDecimal getMonthlyRecoveryPrincipal() {
        return monthlyRecoveryPrincipal;
    }

    public void setMonthlyRecoveryPrincipal(BigDecimal monthlyRecoveryPrincipal) {
        this.monthlyRecoveryPrincipal = monthlyRecoveryPrincipal;
    }

    public BigDecimal getMonthlyRecoveryInterest() {
        return monthlyRecoveryInterest;
    }

    public void setMonthlyRecoveryInterest(BigDecimal monthlyRecoveryInterest) {
        this.monthlyRecoveryInterest = monthlyRecoveryInterest;
    }

    public int getTotalInstallments() {
        return totalInstallments;
    }

    public void setTotalInstallments(int totalInstallments) {
        this.totalInstallments = totalInstallments;
    }

    public int getRecoveredInstallments() {
        return recoveredInstallments;
    }

    public void setRecoveredInstallments(int recoveredInstallments) {
        this.recoveredInstallments = recoveredInstallments;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(BigDecimal outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public CpfLoanApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(CpfLoanApplicationStatus status) {
        this.status = status;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(Instant disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public String getRejectionRemarks() {
        return rejectionRemarks;
    }

    public void setRejectionRemarks(String rejectionRemarks) {
        this.rejectionRemarks = rejectionRemarks;
    }

    public String getApplicationReason() {
        return applicationReason;
    }

    public void setApplicationReason(String applicationReason) {
        this.applicationReason = applicationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getBaseCpfRate() {
        return baseCpfRate;
    }

    public void setBaseCpfRate(BigDecimal baseCpfRate) {
        this.baseCpfRate = baseCpfRate;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public BigDecimal getTotalInterestAmount() {
        return totalInterestAmount;
    }

    public void setTotalInterestAmount(BigDecimal totalInterestAmount) {
        this.totalInterestAmount = totalInterestAmount;
    }

    public BigDecimal getOutstandingInterest() {
        return outstandingInterest;
    }

    public void setOutstandingInterest(BigDecimal outstandingInterest) {
        this.outstandingInterest = outstandingInterest;
    }

    public int getTotalInterestInstallments() {
        return totalInterestInstallments;
    }

    public void setTotalInterestInstallments(int totalInterestInstallments) {
        this.totalInterestInstallments = totalInterestInstallments;
    }

    public int getRecoveredInterestInstallments() {
        return recoveredInterestInstallments;
    }

    public void setRecoveredInterestInstallments(int recoveredInterestInstallments) {
        this.recoveredInterestInstallments = recoveredInterestInstallments;
    }

    public CpfLoanRecoveryPhase getRecoveryPhase() {
        return recoveryPhase;
    }

    public void setRecoveryPhase(CpfLoanRecoveryPhase recoveryPhase) {
        this.recoveryPhase = recoveryPhase;
    }

    public boolean isPreclosed() {
        return preclosed;
    }

    public void setPreclosed(boolean preclosed) {
        this.preclosed = preclosed;
    }

    public Instant getPreclosedAt() {
        return preclosedAt;
    }

    public void setPreclosedAt(Instant preclosedAt) {
        this.preclosedAt = preclosedAt;
    }

    public java.util.UUID getCpfApplicationId() {
        return cpfApplicationId;
    }

    public void setCpfApplicationId(java.util.UUID cpfApplicationId) {
        this.cpfApplicationId = cpfApplicationId;
    }

    @Override
    public String auditEntityName() {
        return "CpfLoanApplication";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("loanApplicationNo", loanApplicationNo);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("loanType", loanType);
        snapshot.put("appliedAmount", appliedAmount);
        snapshot.put("outstandingBalance", outstandingBalance);
        snapshot.put("outstandingInterest", outstandingInterest);
        snapshot.put("recoveryPhase", recoveryPhase);
        snapshot.put("isPreclosed", preclosed);
        snapshot.put("status", status);
        snapshot.put("cpfApplicationId", cpfApplicationId);
        return snapshot;
    }
}
