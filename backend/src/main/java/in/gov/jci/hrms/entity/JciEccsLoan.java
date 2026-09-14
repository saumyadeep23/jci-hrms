package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One JCIECCS Term or Emergency loan. No separate sanction/disburse REST step exists in this module
 * (unlike cpf_loan_applications' Sanction-&gt;Disburse workflow) - POST /api/jcieccs/loans records an
 * already-sanctioned-and-disbursed loan in one step, so a new loan starts at {@link JciEccsLoanStatus#ACTIVE}.
 * monthly_principal_inst/outstanding tracking mirrors the reducing-balance convention already used by
 * LoanService/CpfLoanApplicationService elsewhere in this codebase. Binds to the already-live
 * jcieccs_loan (V87).
 */
@Entity
@Table(name = "jcieccs_loan")
public class JciEccsLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JciEccsMember member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private JciEccsLoanProduct loanProduct;

    @Column(name = "loan_issue_id", nullable = false, length = 30)
    private String loanIssueId;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "sanction_date", nullable = false)
    private LocalDate sanctionDate;

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disbursement_cycle_id", nullable = false)
    private HrmsPayrollCycle disbursementCycle;

    @Column(name = "sanctioned_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(name = "disbursed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "annual_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal annualInterestRate;

    /** Whole rupees, always a multiple of 10 (DB CHECK chk_jcieccs_loan_inst) - see
     * JciEccsAmortizationService for why principal columns across this schema are INTEGER, not NUMERIC. */
    @Column(name = "monthly_principal_inst", nullable = false)
    private int monthlyPrincipalInstallment;

    @Column(name = "outstanding_principal", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JciEccsLoanStatus status = JciEccsLoanStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_loan_id")
    private JciEccsLoan parentLoan;

    @Column(name = "restructuring_count", nullable = false)
    private int restructuringCount;

    @Column(name = "topup_count", nullable = false)
    private int topupCount;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "closure_reason", length = 100)
    private String closureReason;

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

    protected JciEccsLoan() {
    }

    public JciEccsLoan(JciEccsMember member, JciEccsLoanProduct loanProduct, String loanIssueId, LocalDate applicationDate,
                        LocalDate sanctionDate, LocalDate disbursementDate, HrmsPayrollCycle disbursementCycle,
                        BigDecimal sanctionedAmount, BigDecimal disbursedAmount, int tenureMonths, BigDecimal annualInterestRate,
                        int monthlyPrincipalInstallment, BigDecimal outstandingPrincipal) {
        this.member = member;
        this.loanProduct = loanProduct;
        this.loanIssueId = loanIssueId;
        this.applicationDate = applicationDate;
        this.sanctionDate = sanctionDate;
        this.disbursementDate = disbursementDate;
        this.disbursementCycle = disbursementCycle;
        this.sanctionedAmount = sanctionedAmount;
        this.disbursedAmount = disbursedAmount;
        this.tenureMonths = tenureMonths;
        this.annualInterestRate = annualInterestRate;
        this.monthlyPrincipalInstallment = monthlyPrincipalInstallment;
        this.outstandingPrincipal = outstandingPrincipal;
    }

    public Long getId() {
        return id;
    }

    public JciEccsMember getMember() {
        return member;
    }

    public JciEccsLoanProduct getLoanProduct() {
        return loanProduct;
    }

    public String getLoanIssueId() {
        return loanIssueId;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public HrmsPayrollCycle getDisbursementCycle() {
        return disbursementCycle;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount;
    }

    public BigDecimal getDisbursedAmount() {
        return disbursedAmount;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public int getMonthlyPrincipalInstallment() {
        return monthlyPrincipalInstallment;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) {
        this.outstandingPrincipal = outstandingPrincipal;
    }

    public JciEccsLoanStatus getStatus() {
        return status;
    }

    public void setStatus(JciEccsLoanStatus status) {
        this.status = status;
    }

    public JciEccsLoan getParentLoan() {
        return parentLoan;
    }

    public void setParentLoan(JciEccsLoan parentLoan) {
        this.parentLoan = parentLoan;
    }

    public int getRestructuringCount() {
        return restructuringCount;
    }

    public void setRestructuringCount(int restructuringCount) {
        this.restructuringCount = restructuringCount;
    }

    public int getTopupCount() {
        return topupCount;
    }

    public void setTopupCount(int topupCount) {
        this.topupCount = topupCount;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
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
