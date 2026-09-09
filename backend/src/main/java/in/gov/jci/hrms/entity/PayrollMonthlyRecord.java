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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** One employee's staged/final monthly payroll figures for one PayrollBatch - PayrollComputationService.processBatch()'s output row; the per-head breakdown lives in PayrollMonthlyHeadItem. */
@Entity
@Table(name = "payroll_monthly_records")
public class PayrollMonthlyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tran_id")
    private Long tranId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private PayrollBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "emp_code", nullable = false, length = 20)
    private String empCode;

    @Column(name = "month", nullable = false)
    private int month;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "loc_code", length = 20)
    private String locCode;

    @Column(name = "desgn_code", length = 20)
    private String desgnCode;

    @Column(name = "city_class", length = 5)
    private String cityClass;

    @Column(name = "pay_pattern", length = 10)
    private String payPattern = "IDA";

    @Column(name = "days_in_month", nullable = false)
    private int daysInMonth;

    @Column(name = "days_present", nullable = false, precision = 4, scale = 1)
    private BigDecimal daysPresent = BigDecimal.ZERO;

    @Column(name = "days_lop", nullable = false, precision = 4, scale = 1)
    private BigDecimal daysLop = BigDecimal.ZERO;

    @Column(name = "basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicPay = BigDecimal.ZERO;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "is_salary_held", nullable = false)
    private boolean salaryHeld;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected PayrollMonthlyRecord() {
    }

    public PayrollMonthlyRecord(PayrollBatch batch, Employee employee, String empCode, int month, int year,
                                 String locCode, String desgnCode, String cityClass, String payPattern, int daysInMonth) {
        this.batch = batch;
        this.employee = employee;
        this.empCode = empCode;
        this.month = month;
        this.year = year;
        this.locCode = locCode;
        this.desgnCode = desgnCode;
        this.cityClass = cityClass;
        if (payPattern != null) {
            this.payPattern = payPattern;
        }
        this.daysInMonth = daysInMonth;
    }

    public Long getTranId() {
        return tranId;
    }

    public PayrollBatch getBatch() {
        return batch;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getEmpCode() {
        return empCode;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public String getLocCode() {
        return locCode;
    }

    public String getDesgnCode() {
        return desgnCode;
    }

    public String getCityClass() {
        return cityClass;
    }

    public String getPayPattern() {
        return payPattern;
    }

    public int getDaysInMonth() {
        return daysInMonth;
    }

    public BigDecimal getDaysPresent() {
        return daysPresent;
    }

    public void setDaysPresent(BigDecimal daysPresent) {
        this.daysPresent = daysPresent;
    }

    public BigDecimal getDaysLop() {
        return daysLop;
    }

    public void setDaysLop(BigDecimal daysLop) {
        this.daysLop = daysLop;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public void setBasicPay(BigDecimal basicPay) {
        this.basicPay = basicPay;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public boolean isSalaryHeld() {
        return salaryHeld;
    }

    public void setSalaryHeld(boolean salaryHeld) {
        this.salaryHeld = salaryHeld;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
