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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only ledger entry - no updated_at/deleted_at column, matching
 * mobile_punches/audit_logs' immutable-log pattern.
 */
@Entity
@Table(name = "loan_repayments")
@EntityListeners(AuditableEntityListener.class)
public class LoanRepayment implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private EmployeeLoan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_source", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private RepaymentSource repaymentSource;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalComponent;

    @Column(name = "interest_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestComponent;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoanRepayment() {
    }

    public LoanRepayment(EmployeeLoan loan, RepaymentSource repaymentSource, BigDecimal amount,
                          BigDecimal principalComponent, BigDecimal interestComponent, LocalDate paymentDate) {
        this.loan = loan;
        this.repaymentSource = repaymentSource;
        this.amount = amount;
        this.principalComponent = principalComponent;
        this.interestComponent = interestComponent;
        this.paymentDate = paymentDate;
    }

    public Long getId() {
        return id;
    }

    public EmployeeLoan getLoan() {
        return loan;
    }

    public RepaymentSource getRepaymentSource() {
        return repaymentSource;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrincipalComponent() {
        return principalComponent;
    }

    public BigDecimal getInterestComponent() {
        return interestComponent;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "LoanRepayment";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("loanId", loan != null ? loan.getId() : null);
        snapshot.put("repaymentSource", repaymentSource);
        snapshot.put("amount", amount);
        snapshot.put("principalComponent", principalComponent);
        snapshot.put("interestComponent", interestComponent);
        snapshot.put("paymentDate", paymentDate);
        snapshot.put("payrollRunId", payrollRun != null ? payrollRun.getId() : null);
        snapshot.put("transactionReference", transactionReference);
        return snapshot;
    }
}
