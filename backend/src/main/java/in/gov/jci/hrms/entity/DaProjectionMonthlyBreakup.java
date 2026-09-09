package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One employee's one retro (or current-open) month within a DaProjectionBatch simulation. For a
 * finalized month, actualBasicPay/totalDays/paidDays are read straight from payroll_monthly_records
 * (via the matching payroll_batches row for that sal_month/sal_year) - the source the DB schema
 * genuinely wires "exact historical figures" through, per IdaProjectionEngineService's own javadoc. For
 * the still-open current month, actualBasicPay comes from the employee's current RegularPayFixation and
 * paidDays is assumed to equal totalDays (full month).
 */
@Entity
@Table(name = "da_projection_monthly_breakups")
public class DaProjectionMonthlyBreakup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projection_employee_id", nullable = false)
    private DaProjectionEmployee projectionEmployee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private DaProjectionBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "sal_month", nullable = false)
    private int salMonth;

    @Column(name = "sal_year", nullable = false)
    private int salYear;

    @Column(name = "month_label", nullable = false, length = 15)
    private String monthLabel;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "paid_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal paidDays;

    @Column(name = "actual_basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualBasicPay;

    @Column(name = "old_da_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal oldDaRate;

    @Column(name = "new_da_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal newDaRate;

    @Column(name = "delta_da", nullable = false, precision = 12, scale = 2)
    private BigDecimal deltaDa;

    @Column(name = "employee_cpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employeeCpfArrear = BigDecimal.ZERO;

    @Column(name = "employer_jcpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerJcpfArrear = BigDecimal.ZERO;

    @Column(name = "employee_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employeeNpsArrear = BigDecimal.ZERO;

    @Column(name = "employer_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerNpsArrear = BigDecimal.ZERO;

    @Column(name = "net_monthly_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal netMonthlyArrear;

    @Column(name = "employer_cost_monthly", nullable = false, precision = 12, scale = 2)
    private BigDecimal employerCostMonthly;

    protected DaProjectionMonthlyBreakup() {
    }

    public DaProjectionMonthlyBreakup(DaProjectionEmployee projectionEmployee, DaProjectionBatch batch, Employee employee,
                                       int salMonth, int salYear, String monthLabel, int totalDays, BigDecimal paidDays,
                                       BigDecimal actualBasicPay, BigDecimal oldDaRate, BigDecimal newDaRate, BigDecimal deltaDa) {
        this.projectionEmployee = projectionEmployee;
        this.batch = batch;
        this.employee = employee;
        this.salMonth = salMonth;
        this.salYear = salYear;
        this.monthLabel = monthLabel;
        this.totalDays = totalDays;
        this.paidDays = paidDays;
        this.actualBasicPay = actualBasicPay;
        this.oldDaRate = oldDaRate;
        this.newDaRate = newDaRate;
        this.deltaDa = deltaDa;
    }

    public Long getId() {
        return id;
    }

    public DaProjectionEmployee getProjectionEmployee() {
        return projectionEmployee;
    }

    public DaProjectionBatch getBatch() {
        return batch;
    }

    public Employee getEmployee() {
        return employee;
    }

    public int getSalMonth() {
        return salMonth;
    }

    public int getSalYear() {
        return salYear;
    }

    public String getMonthLabel() {
        return monthLabel;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public BigDecimal getPaidDays() {
        return paidDays;
    }

    public BigDecimal getActualBasicPay() {
        return actualBasicPay;
    }

    public BigDecimal getOldDaRate() {
        return oldDaRate;
    }

    public BigDecimal getNewDaRate() {
        return newDaRate;
    }

    public BigDecimal getDeltaDa() {
        return deltaDa;
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

    public BigDecimal getNetMonthlyArrear() {
        return netMonthlyArrear;
    }

    public void setNetMonthlyArrear(BigDecimal netMonthlyArrear) {
        this.netMonthlyArrear = netMonthlyArrear;
    }

    public BigDecimal getEmployerCostMonthly() {
        return employerCostMonthly;
    }

    public void setEmployerCostMonthly(BigDecimal employerCostMonthly) {
        this.employerCostMonthly = employerCostMonthly;
    }
}
