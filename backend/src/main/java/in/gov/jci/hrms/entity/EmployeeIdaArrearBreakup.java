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
 * One employee's one retro-month realized (JIT, at-drawal-time) IDA arrear - the auditable record
 * IdaArrearComputationService.materializeRealizedArrears() persists once a DA order is actually
 * disbursed, as opposed to DaProjectionMonthlyBreakup which is only ever a simulation/estimate.
 * payroll_run_id is NOT NULL and FKs to payroll_runs (the older, still-active cycle-based
 * PayrollRunService) even though the historical basic/days figures this row is built from are read out
 * of payroll_monthly_records (FK'd to payroll_batches, the newer unified engine) - the two payroll
 * subsystems coexist in this schema (see PayrollBatch's own javadoc), and this table's schema commits to
 * the PayrollRun linkage. IdaArrearComputationService resolves-or-creates the PayrollRun row for the
 * drawal cycle purely to satisfy this FK; the actual Head 14/29/62 and Stat Head 11/12/14/15 postings go
 * through the drawal month's PayrollBatch, since payroll_monthly_head_items/payroll_monthly_statutory_items
 * are only reachable via a PayrollBatch-linked PayrollMonthlyRecord.
 */
@Entity
@Table(name = "employee_ida_arrear_breakups")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeIdaArrearBreakup implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "da_rate_history_id", nullable = false)
    private DaRateHistory daRateHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @Column(name = "retro_month", nullable = false)
    private int retroMonth;

    @Column(name = "retro_year", nullable = false)
    private int retroYear;

    @Column(name = "historical_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal historicalBasic;

    @Column(name = "total_calendar_days", nullable = false)
    private int totalCalendarDays;

    @Column(name = "paid_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal paidDays;

    @Column(name = "old_ida_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal oldIdaRate;

    @Column(name = "new_ida_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal newIdaRate;

    @Column(name = "delta_ida_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal deltaIdaRate;

    @Column(name = "gross_ida_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossIdaArrear;

    @Column(name = "employee_cpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employeeCpfArrear = BigDecimal.ZERO;

    @Column(name = "employer_jcpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerJcpfArrear = BigDecimal.ZERO;

    @Column(name = "employee_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employeeNpsArrear = BigDecimal.ZERO;

    @Column(name = "employer_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerNpsArrear = BigDecimal.ZERO;

    @Column(name = "net_ida_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal netIdaArrear;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected EmployeeIdaArrearBreakup() {
    }

    public EmployeeIdaArrearBreakup(Employee employee, DaRateHistory daRateHistory, PayrollRun payrollRun,
                                     int retroMonth, int retroYear, BigDecimal historicalBasic, int totalCalendarDays,
                                     BigDecimal paidDays, BigDecimal oldIdaRate, BigDecimal newIdaRate,
                                     BigDecimal deltaIdaRate, BigDecimal grossIdaArrear) {
        this.employee = employee;
        this.daRateHistory = daRateHistory;
        this.payrollRun = payrollRun;
        this.retroMonth = retroMonth;
        this.retroYear = retroYear;
        this.historicalBasic = historicalBasic;
        this.totalCalendarDays = totalCalendarDays;
        this.paidDays = paidDays;
        this.oldIdaRate = oldIdaRate;
        this.newIdaRate = newIdaRate;
        this.deltaIdaRate = deltaIdaRate;
        this.grossIdaArrear = grossIdaArrear;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DaRateHistory getDaRateHistory() {
        return daRateHistory;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public int getRetroMonth() {
        return retroMonth;
    }

    public int getRetroYear() {
        return retroYear;
    }

    public BigDecimal getHistoricalBasic() {
        return historicalBasic;
    }

    public int getTotalCalendarDays() {
        return totalCalendarDays;
    }

    public BigDecimal getPaidDays() {
        return paidDays;
    }

    public BigDecimal getOldIdaRate() {
        return oldIdaRate;
    }

    public BigDecimal getNewIdaRate() {
        return newIdaRate;
    }

    public BigDecimal getDeltaIdaRate() {
        return deltaIdaRate;
    }

    public BigDecimal getGrossIdaArrear() {
        return grossIdaArrear;
    }

    public BigDecimal getEmployeeCpfArrear() {
        return employeeCpfArrear;
    }

    public void setEmployeeCpfArrear(BigDecimal employeeCpfArrear) {
        this.employeeCpfArrear = employeeCpfArrear;
    }

    public BigDecimal getEmployerJcpfArrear() {
        return employerJcpfArrear;
    }

    public void setEmployerJcpfArrear(BigDecimal employerJcpfArrear) {
        this.employerJcpfArrear = employerJcpfArrear;
    }

    public BigDecimal getEmployeeNpsArrear() {
        return employeeNpsArrear;
    }

    public void setEmployeeNpsArrear(BigDecimal employeeNpsArrear) {
        this.employeeNpsArrear = employeeNpsArrear;
    }

    public BigDecimal getEmployerNpsArrear() {
        return employerNpsArrear;
    }

    public void setEmployerNpsArrear(BigDecimal employerNpsArrear) {
        this.employerNpsArrear = employerNpsArrear;
    }

    public BigDecimal getNetIdaArrear() {
        return netIdaArrear;
    }

    public void setNetIdaArrear(BigDecimal netIdaArrear) {
        this.netIdaArrear = netIdaArrear;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeIdaArrearBreakup";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("daRateHistoryId", daRateHistory != null ? daRateHistory.getId() : null);
        snapshot.put("retroMonth", retroMonth);
        snapshot.put("retroYear", retroYear);
        snapshot.put("grossIdaArrear", grossIdaArrear);
        snapshot.put("netIdaArrear", netIdaArrear);
        return snapshot;
    }
}
