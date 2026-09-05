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
import java.time.LocalDate;

/**
 * One row per generated settlement (TerminalSettmentService.generate()) -
 * a frozen snapshot of every figure that went into the settlement at
 * generation time (gratuity, DoPT Rule 39 EL/HPL encashment, CPF ledger
 * payout), not a live view - re-running generate() for the same employee
 * produces a new row. V58 migration.
 */
@Entity
@Table(name = "terminal_settlements")
public class TerminalSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clearance_request_id")
    private ExitClearanceRequest clearanceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "separation_type", nullable = false, length = 50)
    private SeparationType separationType;

    @Column(name = "separation_date", nullable = false)
    private LocalDate separationDate;

    @Column(name = "last_basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal lastBasicPay;

    @Column(name = "da_rate_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal daRatePercentage;

    @Column(name = "da_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal daAmount;

    @Column(name = "qualifying_service_years", nullable = false)
    private Integer qualifyingServiceYears;

    @Column(name = "qualifying_service_months", nullable = false)
    private Integer qualifyingServiceMonths;

    @Column(name = "el_balance_at_retirement", nullable = false, precision = 5, scale = 2)
    private BigDecimal elBalanceAtRetirement;

    @Column(name = "hpl_balance_at_retirement", nullable = false, precision = 5, scale = 2)
    private BigDecimal hplBalanceAtRetirement;

    @Column(name = "el_days_encashed", nullable = false, precision = 5, scale = 2)
    private BigDecimal elDaysEncashed;

    @Column(name = "hpl_days_encashed", nullable = false, precision = 5, scale = 2)
    private BigDecimal hplDaysEncashed;

    @Column(name = "leave_encashment_el_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal leaveEncashmentElAmount;

    @Column(name = "leave_encashment_hpl_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal leaveEncashmentHplAmount;

    @Column(name = "total_leave_encashment", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLeaveEncashment;

    @Column(name = "gratuity_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal gratuityAmount;

    @Column(name = "is_death_gratuity", nullable = false)
    private boolean deathGratuity = false;

    @Column(name = "cpf_employee_balance", precision = 12, scale = 2)
    private BigDecimal cpfEmployeeBalance = BigDecimal.ZERO;

    @Column(name = "cpf_employer_balance", precision = 12, scale = 2)
    private BigDecimal cpfEmployerBalance = BigDecimal.ZERO;

    @Column(name = "cpf_vpf_balance", precision = 12, scale = 2)
    private BigDecimal cpfVpfBalance = BigDecimal.ZERO;

    @Column(name = "cpf_accrued_interest", precision = 12, scale = 2)
    private BigDecimal cpfAccruedInterest = BigDecimal.ZERO;

    @Column(name = "total_cpf_payable", precision = 14, scale = 2)
    private BigDecimal totalCpfPayable = BigDecimal.ZERO;

    @Column(name = "gross_terminal_dues", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossTerminalDues;

    @Column(name = "total_recoveries_deductions", precision = 12, scale = 2)
    private BigDecimal totalRecoveriesDeductions = BigDecimal.ZERO;

    @Column(name = "net_terminal_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal netTerminalPayable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TerminalSettlementStatus status = TerminalSettlementStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TerminalSettlement() {
    }

    public TerminalSettlement(Employee employee, ExitClearanceRequest clearanceRequest, SeparationType separationType,
                               LocalDate separationDate, BigDecimal lastBasicPay, BigDecimal daRatePercentage, BigDecimal daAmount,
                               Integer qualifyingServiceYears, Integer qualifyingServiceMonths,
                               BigDecimal elBalanceAtRetirement, BigDecimal hplBalanceAtRetirement,
                               BigDecimal elDaysEncashed, BigDecimal hplDaysEncashed,
                               BigDecimal leaveEncashmentElAmount, BigDecimal leaveEncashmentHplAmount, BigDecimal totalLeaveEncashment,
                               BigDecimal gratuityAmount, boolean deathGratuity,
                               BigDecimal cpfEmployeeBalance, BigDecimal cpfEmployerBalance, BigDecimal cpfVpfBalance, BigDecimal cpfAccruedInterest,
                               BigDecimal totalCpfPayable, BigDecimal grossTerminalDues, BigDecimal totalRecoveriesDeductions, BigDecimal netTerminalPayable) {
        this.employee = employee;
        this.clearanceRequest = clearanceRequest;
        this.separationType = separationType;
        this.separationDate = separationDate;
        this.lastBasicPay = lastBasicPay;
        this.daRatePercentage = daRatePercentage;
        this.daAmount = daAmount;
        this.qualifyingServiceYears = qualifyingServiceYears;
        this.qualifyingServiceMonths = qualifyingServiceMonths;
        this.elBalanceAtRetirement = elBalanceAtRetirement;
        this.hplBalanceAtRetirement = hplBalanceAtRetirement;
        this.elDaysEncashed = elDaysEncashed;
        this.hplDaysEncashed = hplDaysEncashed;
        this.leaveEncashmentElAmount = leaveEncashmentElAmount;
        this.leaveEncashmentHplAmount = leaveEncashmentHplAmount;
        this.totalLeaveEncashment = totalLeaveEncashment;
        this.gratuityAmount = gratuityAmount;
        this.deathGratuity = deathGratuity;
        this.cpfEmployeeBalance = cpfEmployeeBalance;
        this.cpfEmployerBalance = cpfEmployerBalance;
        this.cpfVpfBalance = cpfVpfBalance;
        this.cpfAccruedInterest = cpfAccruedInterest;
        this.totalCpfPayable = totalCpfPayable;
        this.grossTerminalDues = grossTerminalDues;
        this.totalRecoveriesDeductions = totalRecoveriesDeductions;
        this.netTerminalPayable = netTerminalPayable;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public ExitClearanceRequest getClearanceRequest() {
        return clearanceRequest;
    }

    public SeparationType getSeparationType() {
        return separationType;
    }

    public LocalDate getSeparationDate() {
        return separationDate;
    }

    public BigDecimal getLastBasicPay() {
        return lastBasicPay;
    }

    public BigDecimal getDaRatePercentage() {
        return daRatePercentage;
    }

    public BigDecimal getDaAmount() {
        return daAmount;
    }

    public Integer getQualifyingServiceYears() {
        return qualifyingServiceYears;
    }

    public Integer getQualifyingServiceMonths() {
        return qualifyingServiceMonths;
    }

    public BigDecimal getElBalanceAtRetirement() {
        return elBalanceAtRetirement;
    }

    public BigDecimal getHplBalanceAtRetirement() {
        return hplBalanceAtRetirement;
    }

    public BigDecimal getElDaysEncashed() {
        return elDaysEncashed;
    }

    public BigDecimal getHplDaysEncashed() {
        return hplDaysEncashed;
    }

    public BigDecimal getLeaveEncashmentElAmount() {
        return leaveEncashmentElAmount;
    }

    public BigDecimal getLeaveEncashmentHplAmount() {
        return leaveEncashmentHplAmount;
    }

    public BigDecimal getTotalLeaveEncashment() {
        return totalLeaveEncashment;
    }

    public BigDecimal getGratuityAmount() {
        return gratuityAmount;
    }

    public boolean isDeathGratuity() {
        return deathGratuity;
    }

    public BigDecimal getCpfEmployeeBalance() {
        return cpfEmployeeBalance;
    }

    public BigDecimal getCpfEmployerBalance() {
        return cpfEmployerBalance;
    }

    public BigDecimal getCpfVpfBalance() {
        return cpfVpfBalance;
    }

    public BigDecimal getCpfAccruedInterest() {
        return cpfAccruedInterest;
    }

    public BigDecimal getTotalCpfPayable() {
        return totalCpfPayable;
    }

    public BigDecimal getGrossTerminalDues() {
        return grossTerminalDues;
    }

    public BigDecimal getTotalRecoveriesDeductions() {
        return totalRecoveriesDeductions;
    }

    public BigDecimal getNetTerminalPayable() {
        return netTerminalPayable;
    }

    public TerminalSettlementStatus getStatus() {
        return status;
    }

    public void setStatus(TerminalSettlementStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
