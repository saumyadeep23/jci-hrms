package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row per employee (see employee_employment_categories_employee_id_key)
 * describing which of the 4 employment tiers they're governed under -
 * PIMS_SPEC.md Step 6. Which of the tier-specific fields are populated
 * depends on employmentCategory - see the chk_regular_data/chk_casual_data/
 * chk_contractual_data/chk_outsourced_data CHECK constraints in V29.
 */
@Entity
@Table(name = "employee_employment_categories")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeEmploymentCategory implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_category", nullable = false, length = 30)
    private EmploymentCategory employmentCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_scale_id")
    private PayScale payScale;

    /** Additive link into the new grade_scale_master (V48/V49) - see that migration's header for why this is separate from payScale/pay_scale_master. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_code", referencedColumnName = "scale_code")
    private GradeScaleMaster gradeScale;

    @Column(name = "regular_basic_pay", precision = 12, scale = 2)
    private BigDecimal regularBasicPay;

    @Column(name = "regular_grade_pay", precision = 10, scale = 2)
    private BigDecimal regularGradePay = BigDecimal.ZERO;

    @Column(name = "daily_wage_rate", precision = 10, scale = 2)
    private BigDecimal dailyWageRate;

    @Column(name = "wage_revision_order_no", length = 100)
    private String wageRevisionOrderNo;

    @Column(name = "fixed_lump_sum_monthly", precision = 12, scale = 2)
    private BigDecimal fixedLumpSumMonthly;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "contract_ref_order", length = 100)
    private String contractRefOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private VendorMaster vendor;

    @Column(name = "monthly_ctc", precision = 12, scale = 2)
    private BigDecimal monthlyCtc;

    @Column(name = "billing_rate_monthly", precision = 12, scale = 2)
    private BigDecimal billingRateMonthly;

    @Column(name = "agency_employee_id", length = 50)
    private String agencyEmployeeId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeEmploymentCategory() {
    }

    public EmployeeEmploymentCategory(Employee employee, EmploymentCategory employmentCategory) {
        this.employee = employee;
        this.employmentCategory = employmentCategory;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmploymentCategory getEmploymentCategory() {
        return employmentCategory;
    }

    public void setEmploymentCategory(EmploymentCategory employmentCategory) {
        this.employmentCategory = employmentCategory;
    }

    public PayScale getPayScale() {
        return payScale;
    }

    public void setPayScale(PayScale payScale) {
        this.payScale = payScale;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public void setGradeScale(GradeScaleMaster gradeScale) {
        this.gradeScale = gradeScale;
    }

    public BigDecimal getRegularBasicPay() {
        return regularBasicPay;
    }

    public void setRegularBasicPay(BigDecimal regularBasicPay) {
        this.regularBasicPay = regularBasicPay;
    }

    public BigDecimal getRegularGradePay() {
        return regularGradePay;
    }

    public void setRegularGradePay(BigDecimal regularGradePay) {
        this.regularGradePay = regularGradePay;
    }

    public BigDecimal getDailyWageRate() {
        return dailyWageRate;
    }

    public void setDailyWageRate(BigDecimal dailyWageRate) {
        this.dailyWageRate = dailyWageRate;
    }

    public String getWageRevisionOrderNo() {
        return wageRevisionOrderNo;
    }

    public void setWageRevisionOrderNo(String wageRevisionOrderNo) {
        this.wageRevisionOrderNo = wageRevisionOrderNo;
    }

    public BigDecimal getFixedLumpSumMonthly() {
        return fixedLumpSumMonthly;
    }

    public void setFixedLumpSumMonthly(BigDecimal fixedLumpSumMonthly) {
        this.fixedLumpSumMonthly = fixedLumpSumMonthly;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public void setContractStartDate(LocalDate contractStartDate) {
        this.contractStartDate = contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public void setContractEndDate(LocalDate contractEndDate) {
        this.contractEndDate = contractEndDate;
    }

    public String getContractRefOrder() {
        return contractRefOrder;
    }

    public void setContractRefOrder(String contractRefOrder) {
        this.contractRefOrder = contractRefOrder;
    }

    public VendorMaster getVendor() {
        return vendor;
    }

    public void setVendor(VendorMaster vendor) {
        this.vendor = vendor;
    }

    public BigDecimal getMonthlyCtc() {
        return monthlyCtc;
    }

    public void setMonthlyCtc(BigDecimal monthlyCtc) {
        this.monthlyCtc = monthlyCtc;
    }

    public BigDecimal getBillingRateMonthly() {
        return billingRateMonthly;
    }

    public void setBillingRateMonthly(BigDecimal billingRateMonthly) {
        this.billingRateMonthly = billingRateMonthly;
    }

    public String getAgencyEmployeeId() {
        return agencyEmployeeId;
    }

    public void setAgencyEmployeeId(String agencyEmployeeId) {
        this.agencyEmployeeId = agencyEmployeeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeEmploymentCategory";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("employmentCategory", employmentCategory);
        snapshot.put("vendorId", vendor != null ? vendor.getId() : null);
        snapshot.put("active", active);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
