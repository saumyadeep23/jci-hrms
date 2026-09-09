package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One CCS(CCA) Rules-style suspension spell. idx_suspension_emp_active (partial, WHERE status =
 * 'UNDER_SUSPENSION') is what SuspensionLifecycleService/PayrollBatchComputationService rely on to
 * resolve "the" active suspension for an employee - at most one per employee at a time in practice,
 * though nothing in the schema enforces that beyond application logic. arrearsPayrollRun/isArrearsSettled
 * track whether revokeAndRegularize()'s computed back-pay arrear has actually been disbursed yet - see
 * SuspensionLifecycleService's own javadoc for why the arrear amount itself isn't persisted here.
 */
@Entity
@Table(name = "employee_suspension_records")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeSuspensionRecord implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "suspension_order_no", nullable = false, length = 100)
    private String suspensionOrderNo;

    @Column(name = "suspension_order_date", nullable = false)
    private LocalDate suspensionOrderDate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "hq_station", nullable = false, length = 100)
    private String hqStation;

    @Column(name = "initial_subsistence_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal initialSubsistencePercentage = new BigDecimal("50.00");

    @Column(name = "current_subsistence_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentSubsistencePercentage = new BigDecimal("50.00");

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "review_order_no", length = 100)
    private String reviewOrderNo;

    @Column(name = "review_remarks")
    private String reviewRemarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SuspensionStatus status = SuspensionStatus.UNDER_SUSPENSION;

    @Column(name = "revocation_order_no", length = 100)
    private String revocationOrderNo;

    @Column(name = "revocation_order_date")
    private LocalDate revocationOrderDate;

    @Column(name = "revocation_effective_date")
    private LocalDate revocationEffectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "regularization_type", length = 30)
    private RegularizationType regularizationType;

    @Column(name = "regularization_remarks")
    private String regularizationRemarks;

    @Column(name = "is_arrears_settled", nullable = false)
    private boolean arrearsSettled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrears_payroll_run_id")
    private PayrollRun arrearsPayrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** month/year-filtered by the repository's own JOIN FETCH ... ON clause (findActiveSuspensionsWithNecStatus) - otherwise holds every month's NEC row. */
    @OneToMany(mappedBy = "suspension", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("salYear DESC, salMonth DESC")
    private List<EmployeeSuspensionNec> necRecords = new ArrayList<>();

    protected EmployeeSuspensionRecord() {
    }

    public EmployeeSuspensionRecord(Employee employee, String suspensionOrderNo, LocalDate suspensionOrderDate,
                                     LocalDate effectiveFrom, String hqStation, Employee createdBy) {
        this.employee = employee;
        this.suspensionOrderNo = suspensionOrderNo;
        this.suspensionOrderDate = suspensionOrderDate;
        this.effectiveFrom = effectiveFrom;
        this.hqStation = hqStation;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getSuspensionOrderNo() {
        return suspensionOrderNo;
    }

    public LocalDate getSuspensionOrderDate() {
        return suspensionOrderDate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getHqStation() {
        return hqStation;
    }

    public BigDecimal getInitialSubsistencePercentage() {
        return initialSubsistencePercentage;
    }

    public void setInitialSubsistencePercentage(BigDecimal initialSubsistencePercentage) {
        this.initialSubsistencePercentage = initialSubsistencePercentage;
    }

    public BigDecimal getCurrentSubsistencePercentage() {
        return currentSubsistencePercentage;
    }

    public void setCurrentSubsistencePercentage(BigDecimal currentSubsistencePercentage) {
        this.currentSubsistencePercentage = currentSubsistencePercentage;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getReviewOrderNo() {
        return reviewOrderNo;
    }

    public void setReviewOrderNo(String reviewOrderNo) {
        this.reviewOrderNo = reviewOrderNo;
    }

    public String getReviewRemarks() {
        return reviewRemarks;
    }

    public void setReviewRemarks(String reviewRemarks) {
        this.reviewRemarks = reviewRemarks;
    }

    public SuspensionStatus getStatus() {
        return status;
    }

    public void setStatus(SuspensionStatus status) {
        this.status = status;
    }

    public String getRevocationOrderNo() {
        return revocationOrderNo;
    }

    public void setRevocationOrderNo(String revocationOrderNo) {
        this.revocationOrderNo = revocationOrderNo;
    }

    public LocalDate getRevocationOrderDate() {
        return revocationOrderDate;
    }

    public void setRevocationOrderDate(LocalDate revocationOrderDate) {
        this.revocationOrderDate = revocationOrderDate;
    }

    public LocalDate getRevocationEffectiveDate() {
        return revocationEffectiveDate;
    }

    public void setRevocationEffectiveDate(LocalDate revocationEffectiveDate) {
        this.revocationEffectiveDate = revocationEffectiveDate;
    }

    public RegularizationType getRegularizationType() {
        return regularizationType;
    }

    public void setRegularizationType(RegularizationType regularizationType) {
        this.regularizationType = regularizationType;
    }

    public String getRegularizationRemarks() {
        return regularizationRemarks;
    }

    public void setRegularizationRemarks(String regularizationRemarks) {
        this.regularizationRemarks = regularizationRemarks;
    }

    public boolean isArrearsSettled() {
        return arrearsSettled;
    }

    public void setArrearsSettled(boolean arrearsSettled) {
        this.arrearsSettled = arrearsSettled;
    }

    public PayrollRun getArrearsPayrollRun() {
        return arrearsPayrollRun;
    }

    public void setArrearsPayrollRun(PayrollRun arrearsPayrollRun) {
        this.arrearsPayrollRun = arrearsPayrollRun;
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

    public List<EmployeeSuspensionNec> getNecRecords() {
        return necRecords;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeSuspensionRecord";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("suspensionOrderNo", suspensionOrderNo);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("currentSubsistencePercentage", currentSubsistencePercentage);
        snapshot.put("status", status);
        snapshot.put("regularizationType", regularizationType);
        return snapshot;
    }
}
