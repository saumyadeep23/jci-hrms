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
 * A Children Education Allowance / Hostel Subsidy claim for one dependent child, one academic year.
 * Table employee_cea_claims already existed in the shared dev database before this entity did (same
 * situation as several other tables this session - see V72's own comment); this maps it as-is,
 * including its one real naming quirk: the payroll-linkage column is named payroll_run_id, but this
 * codebase's actual monthly payroll engine (PayrollBatchComputationService) runs against PayrollBatch/
 * payroll_batches, not a PayrollRun - see getPayrollBatch()'s own note. There is no deleted_at column
 * on this table - a CEA claim is never soft-deleted, only ever moved through claimStatus (including to
 * REJECTED).
 */
@Entity
@Table(name = "employee_cea_claims")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeCeaClaim implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_no", nullable = false, length = 50, unique = true)
    private String claimNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dependent_id", nullable = false)
    private EmployeeDependent dependent;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private CeaClaimType claimType = CeaClaimType.CEA;

    @Column(name = "school_name", nullable = false, length = 200)
    private String schoolName;

    @Column(name = "school_reg_no", length = 100)
    private String schoolRegNo;

    @Column(name = "standard_class", nullable = false, length = 20)
    private String standardClass;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "admissible_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal admissibleAmount;

    @Column(name = "passed_amount", precision = 12, scale = 2)
    private BigDecimal passedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 30)
    private CeaClaimStatus claimStatus = CeaClaimStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_officer")
    private Employee verifiedByOfficer;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "sanction_order_no", length = 100)
    private String sanctionOrderNo;

    @Column(name = "sanction_date")
    private LocalDate sanctionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sanctioned_by_officer")
    private Employee sanctionedByOfficer;

    @Column(name = "bill_no", length = 100)
    private String billNo;

    @Column(name = "bill_date")
    private LocalDate billDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passed_by_officer")
    private Employee passedByOfficer;

    @Column(name = "passed_at")
    private Instant passedAt;

    @Column(name = "is_payroll_processed", nullable = false)
    private boolean payrollProcessed = false;

    /**
     * Column is named payroll_run_id (pre-existing, no FK constraint) but stores a PayrollBatch id -
     * see this class's own javadoc for why. Mirrors LeaveEncashmentApplication.payrollBatch, the
     * equivalent linkage for the same engine.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollBatch payrollBatch;

    @Column(name = "supporting_doc_ref", length = 100)
    private String supportingDocRef;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeCeaClaim() {
    }

    public EmployeeCeaClaim(String claimNo, Employee employee, EmployeeDependent dependent, String academicYear,
                             CeaClaimType claimType, String schoolName, String standardClass,
                             LocalDate periodFrom, LocalDate periodTo, BigDecimal claimedAmount, BigDecimal admissibleAmount) {
        this.claimNo = claimNo;
        this.employee = employee;
        this.dependent = dependent;
        this.academicYear = academicYear;
        this.claimType = claimType;
        this.schoolName = schoolName;
        this.standardClass = standardClass;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.claimedAmount = claimedAmount;
        this.admissibleAmount = admissibleAmount;
    }

    public Long getId() {
        return id;
    }

    public String getClaimNo() {
        return claimNo;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeDependent getDependent() {
        return dependent;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public CeaClaimType getClaimType() {
        return claimType;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getSchoolRegNo() {
        return schoolRegNo;
    }

    public void setSchoolRegNo(String schoolRegNo) {
        this.schoolRegNo = schoolRegNo;
    }

    public String getStandardClass() {
        return standardClass;
    }

    public void setStandardClass(String standardClass) {
        this.standardClass = standardClass;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getAdmissibleAmount() {
        return admissibleAmount;
    }

    public void setAdmissibleAmount(BigDecimal admissibleAmount) {
        this.admissibleAmount = admissibleAmount;
    }

    public BigDecimal getPassedAmount() {
        return passedAmount;
    }

    public void setPassedAmount(BigDecimal passedAmount) {
        this.passedAmount = passedAmount;
    }

    public CeaClaimStatus getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(CeaClaimStatus claimStatus) {
        this.claimStatus = claimStatus;
    }

    public Employee getVerifiedByOfficer() {
        return verifiedByOfficer;
    }

    public void setVerifiedByOfficer(Employee verifiedByOfficer) {
        this.verifiedByOfficer = verifiedByOfficer;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getSanctionOrderNo() {
        return sanctionOrderNo;
    }

    public void setSanctionOrderNo(String sanctionOrderNo) {
        this.sanctionOrderNo = sanctionOrderNo;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public void setSanctionDate(LocalDate sanctionDate) {
        this.sanctionDate = sanctionDate;
    }

    public Employee getSanctionedByOfficer() {
        return sanctionedByOfficer;
    }

    public void setSanctionedByOfficer(Employee sanctionedByOfficer) {
        this.sanctionedByOfficer = sanctionedByOfficer;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public Employee getPassedByOfficer() {
        return passedByOfficer;
    }

    public void setPassedByOfficer(Employee passedByOfficer) {
        this.passedByOfficer = passedByOfficer;
    }

    public Instant getPassedAt() {
        return passedAt;
    }

    public void setPassedAt(Instant passedAt) {
        this.passedAt = passedAt;
    }

    public boolean isPayrollProcessed() {
        return payrollProcessed;
    }

    public void setPayrollProcessed(boolean payrollProcessed) {
        this.payrollProcessed = payrollProcessed;
    }

    public PayrollBatch getPayrollBatch() {
        return payrollBatch;
    }

    public void setPayrollBatch(PayrollBatch payrollBatch) {
        this.payrollBatch = payrollBatch;
    }

    public String getSupportingDocRef() {
        return supportingDocRef;
    }

    public void setSupportingDocRef(String supportingDocRef) {
        this.supportingDocRef = supportingDocRef;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeCeaClaim";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("claimNo", claimNo);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("dependentId", dependent != null ? dependent.getId() : null);
        snapshot.put("academicYear", academicYear);
        snapshot.put("claimType", claimType);
        snapshot.put("claimedAmount", claimedAmount);
        snapshot.put("admissibleAmount", admissibleAmount);
        snapshot.put("passedAmount", passedAmount);
        snapshot.put("claimStatus", claimStatus);
        snapshot.put("isPayrollProcessed", payrollProcessed);
        return snapshot;
    }
}
