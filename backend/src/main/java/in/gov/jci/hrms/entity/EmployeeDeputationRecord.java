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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One deputation spell (out to another organization, or in from one). idx_deputation_emp_active
 * (partial, WHERE status = 'ACTIVE') is what DeputationLifecycleService/PayrollBatchComputationService
 * rely on to resolve an employee's current active deputation.
 */
@Entity
@Table(name = "employee_deputation_records")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeDeputationRecord implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "deputation_direction", nullable = false, length = 20)
    private DeputationDirection deputationDirection;

    @Column(name = "organization_name", nullable = false, length = 200)
    private String organizationName;

    @Column(name = "organization_type", nullable = false, length = 50)
    private String organizationType;

    @Column(name = "posting_station", nullable = false, length = 100)
    private String postingStation;

    @Column(name = "is_same_station", nullable = false)
    private boolean sameStation = false;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "extension_valid_up_to")
    private LocalDate extensionValidUpTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_option", nullable = false, length = 40)
    private PayOption payOption;

    /** DPE 3rd PRC: 5% (same station) or 10% (different station) of Basic+DA - see DeputationLifecycleService.initiateDeputation(). */
    @Column(name = "deputation_allowance_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal deputationAllowanceRate = BigDecimal.ZERO;

    /** DPE 3rd PRC: Rs. 4,500 (same station) or Rs. 9,000 (different station). */
    @Column(name = "deputation_allowance_cap", nullable = false, precision = 10, scale = 2)
    private BigDecimal deputationAllowanceCap = BigDecimal.ZERO;

    @Column(name = "lspc_applicable", nullable = false)
    private boolean lspcApplicable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "lspc_borne_by", nullable = false, length = 30)
    private LspcBorneBy lspcBorneBy = LspcBorneBy.BORROWING_ORG;

    @Column(name = "lspc_monthly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal lspcMonthlyRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeputationStatus status = DeputationStatus.ACTIVE;

    @Column(name = "repatriation_order_no", length = 100)
    private String repatriationOrderNo;

    @Column(name = "repatriation_date")
    private LocalDate repatriationDate;

    @Column(name = "remarks")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected EmployeeDeputationRecord() {
    }

    public EmployeeDeputationRecord(Employee employee, DeputationDirection deputationDirection, String organizationName,
                                     String organizationType, String postingStation, boolean sameStation,
                                     LocalDate periodFrom, LocalDate periodTo, PayOption payOption, Employee createdBy) {
        this.employee = employee;
        this.deputationDirection = deputationDirection;
        this.organizationName = organizationName;
        this.organizationType = organizationType;
        this.postingStation = postingStation;
        this.sameStation = sameStation;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.payOption = payOption;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DeputationDirection getDeputationDirection() {
        return deputationDirection;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationType() {
        return organizationType;
    }

    public String getPostingStation() {
        return postingStation;
    }

    public boolean isSameStation() {
        return sameStation;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public LocalDate getExtensionValidUpTo() {
        return extensionValidUpTo;
    }

    public void setExtensionValidUpTo(LocalDate extensionValidUpTo) {
        this.extensionValidUpTo = extensionValidUpTo;
    }

    public PayOption getPayOption() {
        return payOption;
    }

    public BigDecimal getDeputationAllowanceRate() {
        return deputationAllowanceRate;
    }

    public void setDeputationAllowanceRate(BigDecimal deputationAllowanceRate) {
        this.deputationAllowanceRate = deputationAllowanceRate;
    }

    public BigDecimal getDeputationAllowanceCap() {
        return deputationAllowanceCap;
    }

    public void setDeputationAllowanceCap(BigDecimal deputationAllowanceCap) {
        this.deputationAllowanceCap = deputationAllowanceCap;
    }

    public boolean isLspcApplicable() {
        return lspcApplicable;
    }

    public void setLspcApplicable(boolean lspcApplicable) {
        this.lspcApplicable = lspcApplicable;
    }

    public LspcBorneBy getLspcBorneBy() {
        return lspcBorneBy;
    }

    public void setLspcBorneBy(LspcBorneBy lspcBorneBy) {
        this.lspcBorneBy = lspcBorneBy;
    }

    public BigDecimal getLspcMonthlyRate() {
        return lspcMonthlyRate;
    }

    public void setLspcMonthlyRate(BigDecimal lspcMonthlyRate) {
        this.lspcMonthlyRate = lspcMonthlyRate;
    }

    public DeputationStatus getStatus() {
        return status;
    }

    public void setStatus(DeputationStatus status) {
        this.status = status;
    }

    public String getRepatriationOrderNo() {
        return repatriationOrderNo;
    }

    public void setRepatriationOrderNo(String repatriationOrderNo) {
        this.repatriationOrderNo = repatriationOrderNo;
    }

    public LocalDate getRepatriationDate() {
        return repatriationDate;
    }

    public void setRepatriationDate(LocalDate repatriationDate) {
        this.repatriationDate = repatriationDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Employee getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeDeputationRecord";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("deputationDirection", deputationDirection);
        snapshot.put("organizationName", organizationName);
        snapshot.put("periodFrom", periodFrom);
        snapshot.put("periodTo", periodTo);
        snapshot.put("status", status);
        return snapshot;
    }
}
