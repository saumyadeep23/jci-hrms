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

/** One employee's roll-up within a DaProjectionBatch - the per-month figures live in DaProjectionMonthlyBreakup. */
@Entity
@Table(name = "da_projection_employees")
public class DaProjectionEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private DaProjectionBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** "CPF" or "NPS" - matches employee.isNpsEligible() && hasPran(), the same discriminator PayrollBatchComputationService.resolveEmployerContributions() uses. */
    @Column(name = "pension_scheme", nullable = false, length = 10)
    private String pensionScheme;

    @Column(name = "total_months_count", nullable = false)
    private int totalMonthsCount;

    @Column(name = "total_gross_arrears", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGrossArrears = BigDecimal.ZERO;

    @Column(name = "total_employee_cpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEmployeeCpfArrear = BigDecimal.ZERO;

    @Column(name = "total_employer_jcpf_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEmployerJcpfArrear = BigDecimal.ZERO;

    @Column(name = "total_employee_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEmployeeNpsArrear = BigDecimal.ZERO;

    @Column(name = "total_employer_nps_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEmployerNpsArrear = BigDecimal.ZERO;

    @Column(name = "total_leave_encashment_arrear", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLeaveEncashmentArrear = BigDecimal.ZERO;

    @Column(name = "total_net_arrear_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNetArrearPayable = BigDecimal.ZERO;

    @Column(name = "total_employer_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEmployerCost = BigDecimal.ZERO;

    protected DaProjectionEmployee() {
    }

    public DaProjectionEmployee(DaProjectionBatch batch, Employee employee, String pensionScheme) {
        this.batch = batch;
        this.employee = employee;
        this.pensionScheme = pensionScheme;
    }

    public Long getId() {
        return id;
    }

    public DaProjectionBatch getBatch() {
        return batch;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getPensionScheme() {
        return pensionScheme;
    }

    public int getTotalMonthsCount() {
        return totalMonthsCount;
    }

    public void setTotalMonthsCount(int totalMonthsCount) {
        this.totalMonthsCount = totalMonthsCount;
    }

    public BigDecimal getTotalGrossArrears() {
        return totalGrossArrears;
    }

    public void setTotalGrossArrears(BigDecimal totalGrossArrears) {
        this.totalGrossArrears = totalGrossArrears;
    }

    public BigDecimal getTotalEmployeeCpfArrear() {
        return totalEmployeeCpfArrear;
    }

    public void setTotalEmployeeCpfArrear(BigDecimal totalEmployeeCpfArrear) {
        this.totalEmployeeCpfArrear = totalEmployeeCpfArrear;
    }

    public BigDecimal getTotalEmployerJcpfArrear() {
        return totalEmployerJcpfArrear;
    }

    public void setTotalEmployerJcpfArrear(BigDecimal totalEmployerJcpfArrear) {
        this.totalEmployerJcpfArrear = totalEmployerJcpfArrear;
    }

    public BigDecimal getTotalEmployeeNpsArrear() {
        return totalEmployeeNpsArrear;
    }

    public void setTotalEmployeeNpsArrear(BigDecimal totalEmployeeNpsArrear) {
        this.totalEmployeeNpsArrear = totalEmployeeNpsArrear;
    }

    public BigDecimal getTotalEmployerNpsArrear() {
        return totalEmployerNpsArrear;
    }

    public void setTotalEmployerNpsArrear(BigDecimal totalEmployerNpsArrear) {
        this.totalEmployerNpsArrear = totalEmployerNpsArrear;
    }

    public BigDecimal getTotalLeaveEncashmentArrear() {
        return totalLeaveEncashmentArrear;
    }

    public void setTotalLeaveEncashmentArrear(BigDecimal totalLeaveEncashmentArrear) {
        this.totalLeaveEncashmentArrear = totalLeaveEncashmentArrear;
    }

    public BigDecimal getTotalNetArrearPayable() {
        return totalNetArrearPayable;
    }

    public void setTotalNetArrearPayable(BigDecimal totalNetArrearPayable) {
        this.totalNetArrearPayable = totalNetArrearPayable;
    }

    public BigDecimal getTotalEmployerCost() {
        return totalEmployerCost;
    }

    public void setTotalEmployerCost(BigDecimal totalEmployerCost) {
        this.totalEmployerCost = totalEmployerCost;
    }
}
