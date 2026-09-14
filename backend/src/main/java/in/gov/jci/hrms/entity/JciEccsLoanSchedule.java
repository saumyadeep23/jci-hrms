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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One pre-generated installment row of a {@link JciEccsLoan}'s amortization schedule - this module's own
 * concept (the older cpf_loan_applications flow tracks only running balances, no per-installment rows).
 * total_due/total_paid are DB GENERATED columns (principal + interest), read-only from the Java side.
 * principal_due is INTEGER (whole rupees, always a multiple of 10 - see JciEccsAmortizationService).
 * Binds to the already-live jcieccs_loan_schedule (V87).
 */
@Entity
@Table(name = "jcieccs_loan_schedule")
public class JciEccsLoanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private JciEccsLoan loan;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private HrmsPayrollCycle cycle;

    @Column(name = "opening_principal", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingPrincipal;

    @Column(name = "principal_due", nullable = false)
    private int principalDue;

    @Column(name = "interest_due", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "total_due", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalDue;

    @Column(name = "principal_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "total_paid", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "principal_outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal principalOutstanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JciEccsScheduleStatus status = JciEccsScheduleStatus.FUTURE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JciEccsLoanSchedule() {
    }

    public JciEccsLoanSchedule(JciEccsLoan loan, int installmentNo, HrmsPayrollCycle cycle, BigDecimal openingPrincipal,
                                int principalDue, BigDecimal interestDue, BigDecimal principalOutstanding) {
        this.loan = loan;
        this.installmentNo = installmentNo;
        this.cycle = cycle;
        this.openingPrincipal = openingPrincipal;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.principalOutstanding = principalOutstanding;
    }

    public Long getId() {
        return id;
    }

    public JciEccsLoan getLoan() {
        return loan;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public HrmsPayrollCycle getCycle() {
        return cycle;
    }

    public BigDecimal getOpeningPrincipal() {
        return openingPrincipal;
    }

    public int getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public void setPrincipalPaid(BigDecimal principalPaid) {
        this.principalPaid = principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public void setInterestPaid(BigDecimal interestPaid) {
        this.interestPaid = interestPaid;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public BigDecimal getPrincipalOutstanding() {
        return principalOutstanding;
    }

    public void setPrincipalOutstanding(BigDecimal principalOutstanding) {
        this.principalOutstanding = principalOutstanding;
    }

    public JciEccsScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(JciEccsScheduleStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
