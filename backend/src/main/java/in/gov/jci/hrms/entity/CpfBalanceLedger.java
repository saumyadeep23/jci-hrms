package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row per employee (unique employee_id, enforced in the V10 migration).
 * No created_at/deleted_at column - a ledger is provisioned once and only
 * ever updated in place thereafter, matching leave_balances' pattern.
 */
@Entity
@Table(name = "cpf_balance_ledgers")
@EntityListeners(AuditableEntityListener.class)
public class CpfBalanceLedger implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "employee_fund_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal employeeFundBalance = BigDecimal.ZERO;

    @Column(name = "employer_fund_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerFundBalance = BigDecimal.ZERO;

    @Column(name = "vpf_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfBalance = BigDecimal.ZERO;

    @Column(name = "last_interest_credited_date")
    private LocalDate lastInterestCreditedDate;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CpfBalanceLedger() {
    }

    public CpfBalanceLedger(Employee employee, BigDecimal employeeFundBalance, BigDecimal employerFundBalance,
                             BigDecimal vpfBalance) {
        this.employee = employee;
        this.employeeFundBalance = employeeFundBalance;
        this.employerFundBalance = employerFundBalance;
        this.vpfBalance = vpfBalance;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public BigDecimal getEmployeeFundBalance() {
        return employeeFundBalance;
    }

    public void setEmployeeFundBalance(BigDecimal employeeFundBalance) {
        this.employeeFundBalance = employeeFundBalance;
    }

    public BigDecimal getEmployerFundBalance() {
        return employerFundBalance;
    }

    public void setEmployerFundBalance(BigDecimal employerFundBalance) {
        this.employerFundBalance = employerFundBalance;
    }

    public BigDecimal getVpfBalance() {
        return vpfBalance;
    }

    public void setVpfBalance(BigDecimal vpfBalance) {
        this.vpfBalance = vpfBalance;
    }

    public LocalDate getLastInterestCreditedDate() {
        return lastInterestCreditedDate;
    }

    public void setLastInterestCreditedDate(LocalDate lastInterestCreditedDate) {
        this.lastInterestCreditedDate = lastInterestCreditedDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "CpfBalanceLedger";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("employeeFundBalance", employeeFundBalance);
        snapshot.put("employerFundBalance", employerFundBalance);
        snapshot.put("vpfBalance", vpfBalance);
        snapshot.put("lastInterestCreditedDate", lastInterestCreditedDate);
        return snapshot;
    }
}
