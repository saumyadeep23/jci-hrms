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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
