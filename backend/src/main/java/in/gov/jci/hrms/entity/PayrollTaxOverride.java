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

import java.math.BigDecimal;
import java.time.Instant;

/** A Bill Section manual override of the TDS engine's computed monthly deduction, with a mandatory justification (>= 10 chars, DB-enforced) - one per (employee, payroll_year, payroll_month). */
@Entity
@Table(name = "payroll_tax_overrides")
public class PayrollTaxOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Column(name = "payroll_month", nullable = false)
    private int payrollMonth;

    @Column(name = "payroll_year", nullable = false)
    private int payrollYear;

    @Column(name = "calculated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedAmount = BigDecimal.ZERO;

    @Column(name = "overridden_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal overriddenAmount;

    @Column(name = "override_reason", nullable = false, length = 500)
    private String overrideReason;

    @Column(name = "applied_by", nullable = false, length = 100)
    private String appliedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private TaxOverrideScope scope = TaxOverrideScope.MONTH_ONLY;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PayrollTaxOverride() {
    }

    public PayrollTaxOverride(Employee employee, String financialYear, int payrollMonth, int payrollYear,
                               BigDecimal calculatedAmount, BigDecimal overriddenAmount, String overrideReason,
                               String appliedBy, TaxOverrideScope scope) {
        this.employee = employee;
        this.financialYear = financialYear;
        this.payrollMonth = payrollMonth;
        this.payrollYear = payrollYear;
        if (calculatedAmount != null) {
            this.calculatedAmount = calculatedAmount;
        }
        this.overriddenAmount = overriddenAmount;
        this.overrideReason = overrideReason;
        this.appliedBy = appliedBy;
        if (scope != null) {
            this.scope = scope;
        }
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public int getPayrollMonth() {
        return payrollMonth;
    }

    public int getPayrollYear() {
        return payrollYear;
    }

    public BigDecimal getCalculatedAmount() {
        return calculatedAmount;
    }

    public BigDecimal getOverriddenAmount() {
        return overriddenAmount;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public TaxOverrideScope getScope() {
        return scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
