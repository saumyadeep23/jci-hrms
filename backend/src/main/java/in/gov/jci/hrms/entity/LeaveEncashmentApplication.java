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

/** Two-gate (HR then Finance) EL encashment workflow - see V36 for the DB CHECK constraints this entity must stay consistent with (>=15 days for IN_SERVICE_EL, dual-approval-required-for-payroll-eligibility). */
@Entity
@Table(name = "leave_encashment_application")
public class LeaveEncashmentApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "encashment_type", nullable = false, length = 20)
    private EncashmentType encashmentType;

    @Column(name = "el_days_claimed", nullable = false, precision = 5, scale = 2)
    private BigDecimal elDaysClaimed = BigDecimal.ZERO;

    @Column(name = "hpl_days_claimed", nullable = false, precision = 5, scale = 2)
    private BigDecimal hplDaysClaimed = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_approval_status", nullable = false, length = 20)
    private ApprovalStatus hrApprovalStatus = ApprovalStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_approved_by")
    private Employee hrApprovedBy;
    @Column(name = "hr_approved_at")
    private Instant hrApprovedAt;
    @Column(name = "hr_remarks")
    private String hrRemarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "finance_approval_status", nullable = false, length = 20)
    private ApprovalStatus financeApprovalStatus = ApprovalStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finance_approved_by")
    private Employee financeApprovedBy;
    @Column(name = "finance_approved_at")
    private Instant financeApprovedAt;
    @Column(name = "finance_remarks")
    private String financeRemarks;

    @Column(name = "is_payroll_eligible", nullable = false)
    private boolean payrollEligible = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_book_entry_id")
    private EmployeeServiceBook serviceBookEntry;

    /** The DA % actually used to quote gross_amount, snapshotted at apply() time - see V63. */
    @Column(name = "da_rate_applied", precision = 6, scale = 2)
    private BigDecimal daRateApplied;

    /** CPSE formula: (Basic + DA) / 30 * days claimed, computed with daRateApplied at apply() time. */
    @Column(name = "gross_amount", precision = 12, scale = 2)
    private BigDecimal grossAmount;

    /** effective_from of the da_rate_history row daRateApplied was read from. */
    @Column(name = "da_effective_date")
    private LocalDate daEffectiveDate;

    /** Flips false when financeApprove() finds the DA rate has been retroactively revised upward past daRateApplied. */
    @Column(name = "is_arrear_settled", nullable = false)
    private boolean arrearSettled = true;

    /** Top-up owed on top of grossAmount when isArrearSettled is false - see LeaveEncashmentService.financeApprove(). */
    @Column(name = "arrear_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal arrearAmount = BigDecimal.ZERO;

    /** currentDaRate - daRateApplied at the moment checkForRetroactiveArrear() ran - the "+X%" shown alongside daRateApplied's "old" rate. */
    @Column(name = "arrear_da_rate_diff", nullable = false, precision = 6, scale = 2)
    private BigDecimal arrearDaRateDiff = BigDecimal.ZERO;

    /** Set once PayrollRunService.compute() queues the base gross_amount into a run; is_payroll_processed flips true only once that run is finalized. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @Column(name = "is_payroll_processed", nullable = false)
    private boolean payrollProcessed = false;

    /**
     * The arrear can be queued into a LATER payroll run than payrollRun - it's only known once
     * Finance sanctions the claim, which may be after the base amount's own run already closed.
     * isArrearSettled flips true only once THIS run is finalized, mirroring payrollRun/payrollProcessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrear_payroll_run_id")
    private PayrollRun arrearPayrollRun;

    /**
     * The payroll_batches run (unified Payroll Computation Engine - see PayrollBatchComputationService)
     * that has queued this application's base amount into its Head 20 (ENCASH_AMT) total - separate
     * from payrollRun above, which belongs to the older, still-active cycle-based PayrollRunService.
     * Set on processBatch(), cleared on re-run, and never flips payrollProcessed on its own -
     * PayrollBatchComputationService.finalizeBatch() does that, mirroring payrollRun/payrollProcessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_batch_id")
    private PayrollBatch payrollBatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 20)
    private EncashmentApplicationType applicationType = EncashmentApplicationType.REGULAR;

    /** Set only on a DA_ARREAR row - the REGULAR encashment this is a system-generated top-up on. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_application_id")
    private LeaveEncashmentApplication parentApplication;

    /** Set only on a DA_ARREAR row - the DA order (IdaArrearComputationService.materializeRealizedArrears()) that produced it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "da_rate_history_id")
    private DaRateHistory daRateHistory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveEncashmentApplication() {
    }

    public LeaveEncashmentApplication(Employee employee, EncashmentType encashmentType, BigDecimal elDaysClaimed,
                                       BigDecimal hplDaysClaimed) {
        this.employee = employee;
        this.encashmentType = encashmentType;
        this.elDaysClaimed = elDaysClaimed;
        this.hplDaysClaimed = hplDaysClaimed != null ? hplDaysClaimed : BigDecimal.ZERO;
    }

    /** IdaArrearComputationService.materializeRealizedArrears()'s DA_ARREAR child row - a system-generated top-up on an already dual-approved REGULAR encashment, so it's created already approved and payroll-eligible rather than re-entering the HR/Finance queue. */
    public static LeaveEncashmentApplication daArrearChild(LeaveEncashmentApplication parent, DaRateHistory daRateHistory, BigDecimal grossAmount) {
        LeaveEncashmentApplication child = new LeaveEncashmentApplication(parent.employee, parent.encashmentType,
                parent.elDaysClaimed, parent.hplDaysClaimed);
        child.applicationType = EncashmentApplicationType.DA_ARREAR;
        child.parentApplication = parent;
        child.daRateHistory = daRateHistory;
        child.grossAmount = grossAmount;
        child.hrApprovalStatus = ApprovalStatus.APPROVED;
        child.financeApprovalStatus = ApprovalStatus.APPROVED;
        child.payrollEligible = true;
        return child;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EncashmentType getEncashmentType() {
        return encashmentType;
    }

    public BigDecimal getElDaysClaimed() {
        return elDaysClaimed;
    }

    public BigDecimal getHplDaysClaimed() {
        return hplDaysClaimed;
    }

    public ApprovalStatus getHrApprovalStatus() {
        return hrApprovalStatus;
    }

    public void setHrApprovalStatus(ApprovalStatus hrApprovalStatus) {
        this.hrApprovalStatus = hrApprovalStatus;
    }

    public Employee getHrApprovedBy() {
        return hrApprovedBy;
    }

    public void setHrApprovedBy(Employee hrApprovedBy) {
        this.hrApprovedBy = hrApprovedBy;
    }

    public Instant getHrApprovedAt() {
        return hrApprovedAt;
    }

    public void setHrApprovedAt(Instant hrApprovedAt) {
        this.hrApprovedAt = hrApprovedAt;
    }

    public String getHrRemarks() {
        return hrRemarks;
    }

    public void setHrRemarks(String hrRemarks) {
        this.hrRemarks = hrRemarks;
    }

    public ApprovalStatus getFinanceApprovalStatus() {
        return financeApprovalStatus;
    }

    public void setFinanceApprovalStatus(ApprovalStatus financeApprovalStatus) {
        this.financeApprovalStatus = financeApprovalStatus;
    }

    public Employee getFinanceApprovedBy() {
        return financeApprovedBy;
    }

    public void setFinanceApprovedBy(Employee financeApprovedBy) {
        this.financeApprovedBy = financeApprovedBy;
    }

    public Instant getFinanceApprovedAt() {
        return financeApprovedAt;
    }

    public void setFinanceApprovedAt(Instant financeApprovedAt) {
        this.financeApprovedAt = financeApprovedAt;
    }

    public String getFinanceRemarks() {
        return financeRemarks;
    }

    public void setFinanceRemarks(String financeRemarks) {
        this.financeRemarks = financeRemarks;
    }

    public boolean isPayrollEligible() {
        return payrollEligible;
    }

    public void setPayrollEligible(boolean payrollEligible) {
        this.payrollEligible = payrollEligible;
    }

    public EmployeeServiceBook getServiceBookEntry() {
        return serviceBookEntry;
    }

    public void setServiceBookEntry(EmployeeServiceBook serviceBookEntry) {
        this.serviceBookEntry = serviceBookEntry;
    }

    public BigDecimal getDaRateApplied() {
        return daRateApplied;
    }

    public void setDaRateApplied(BigDecimal daRateApplied) {
        this.daRateApplied = daRateApplied;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public LocalDate getDaEffectiveDate() {
        return daEffectiveDate;
    }

    public void setDaEffectiveDate(LocalDate daEffectiveDate) {
        this.daEffectiveDate = daEffectiveDate;
    }

    public boolean isArrearSettled() {
        return arrearSettled;
    }

    public void setArrearSettled(boolean arrearSettled) {
        this.arrearSettled = arrearSettled;
    }

    public BigDecimal getArrearAmount() {
        return arrearAmount;
    }

    public void setArrearAmount(BigDecimal arrearAmount) {
        this.arrearAmount = arrearAmount;
    }

    public BigDecimal getArrearDaRateDiff() {
        return arrearDaRateDiff;
    }

    public void setArrearDaRateDiff(BigDecimal arrearDaRateDiff) {
        this.arrearDaRateDiff = arrearDaRateDiff;
    }

    public PayrollRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(PayrollRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public boolean isPayrollProcessed() {
        return payrollProcessed;
    }

    public void setPayrollProcessed(boolean payrollProcessed) {
        this.payrollProcessed = payrollProcessed;
    }

    public PayrollRun getArrearPayrollRun() {
        return arrearPayrollRun;
    }

    public void setArrearPayrollRun(PayrollRun arrearPayrollRun) {
        this.arrearPayrollRun = arrearPayrollRun;
    }

    public PayrollBatch getPayrollBatch() {
        return payrollBatch;
    }

    public void setPayrollBatch(PayrollBatch payrollBatch) {
        this.payrollBatch = payrollBatch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public EncashmentApplicationType getApplicationType() {
        return applicationType;
    }

    public LeaveEncashmentApplication getParentApplication() {
        return parentApplication;
    }

    public DaRateHistory getDaRateHistory() {
        return daRateHistory;
    }
}
