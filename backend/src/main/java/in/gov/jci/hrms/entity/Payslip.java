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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (payroll_run_id, employee_id) uniqueness is enforced by a plain unique
 * constraint in the V8 migration. is_hold gates disbursement only - the
 * computed amounts are still stored in full even when held, so the payslip
 * reflects actual entitlement rather than a zeroed-out placeholder.
 */
@Entity
@Table(name = "payslips")
@EntityListeners(AuditableEntityListener.class)
public class Payslip implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "total_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEarnings;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "employer_contributions", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerContributions;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "lop_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "is_hold", nullable = false)
    private boolean hold = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payslip() {
    }

    public Payslip(PayrollRun payrollRun, Employee employee, BigDecimal basicPay, BigDecimal totalEarnings,
                    BigDecimal totalDeductions, BigDecimal employerContributions, BigDecimal netPay,
                    BigDecimal lopDays, boolean hold) {
        this.payrollRun = payrollRun;
        this.employee = employee;
        this.basicPay = basicPay;
        this.totalEarnings = totalEarnings;
        this.totalDeductions = totalDeductions;
        this.employerContributions = employerContributions;
        this.netPay = netPay;
        this.lopDays = lopDays;
        this.hold = hold;
    }

    public Long getId() {
        return id;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public Employee getEmployee() {
        return employee;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getEmployerContributions() {
        return employerContributions;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public BigDecimal getLopDays() {
        return lopDays;
    }

    public boolean isHold() {
        return hold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "Payslip";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payrollRunId", payrollRun != null ? payrollRun.getId() : null);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("basicPay", basicPay);
        snapshot.put("totalEarnings", totalEarnings);
        snapshot.put("totalDeductions", totalDeductions);
        snapshot.put("employerContributions", employerContributions);
        snapshot.put("netPay", netPay);
        snapshot.put("lopDays", lopDays);
        snapshot.put("isHold", hold);
        return snapshot;
    }
}
