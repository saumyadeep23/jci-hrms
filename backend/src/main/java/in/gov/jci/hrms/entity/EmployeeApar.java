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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (apar_cycle_id, employee_id) uniqueness is enforced by a partial unique
 * index (WHERE deleted_at IS NULL) in the Flyway migration. reporting/
 * reviewing/accepting officer IDs are assigned explicitly at creation
 * (AparService.initiate()), not resolved dynamically via
 * SupervisorResolutionService - an APAR's designated reporting chain for a
 * given cycle can differ from the employee's live org-hierarchy supervisor
 * at any later point in time.
 */
@Entity
@Table(name = "employee_apars")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeApar implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apar_cycle_id", nullable = false)
    private AparCycle aparCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporting_officer_id", nullable = false)
    private Employee reportingOfficer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewing_officer_id", nullable = false)
    private Employee reviewingOfficer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accepting_authority_id", nullable = false)
    private Employee acceptingAuthority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private EmployeeAparStatus status = EmployeeAparStatus.DRAFT;

    @Column(name = "self_appraisal_text")
    private String selfAppraisalText;

    @Column(name = "reporting_score", precision = 4, scale = 2)
    private BigDecimal reportingScore;

    @Column(name = "reporting_remarks")
    private String reportingRemarks;

    @Column(name = "reviewing_score", precision = 4, scale = 2)
    private BigDecimal reviewingScore;

    @Column(name = "reviewing_remarks")
    private String reviewingRemarks;

    @Column(name = "final_score", precision = 4, scale = 2)
    private BigDecimal finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_grading", length = 20, columnDefinition = "VARCHAR")
    private FinalGrading finalGrading;

    @Column(name = "representation_text")
    private String representationText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeApar() {
    }

    public EmployeeApar(AparCycle aparCycle, Employee employee, Employee reportingOfficer, Employee reviewingOfficer,
                         Employee acceptingAuthority) {
        this.aparCycle = aparCycle;
        this.employee = employee;
        this.reportingOfficer = reportingOfficer;
        this.reviewingOfficer = reviewingOfficer;
        this.acceptingAuthority = acceptingAuthority;
    }

    public Long getId() {
        return id;
    }

    public AparCycle getAparCycle() {
        return aparCycle;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Employee getReportingOfficer() {
        return reportingOfficer;
    }

    public Employee getReviewingOfficer() {
        return reviewingOfficer;
    }

    public Employee getAcceptingAuthority() {
        return acceptingAuthority;
    }

    public EmployeeAparStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeAparStatus status) {
        this.status = status;
    }

    public String getSelfAppraisalText() {
        return selfAppraisalText;
    }

    public void setSelfAppraisalText(String selfAppraisalText) {
        this.selfAppraisalText = selfAppraisalText;
    }

    public BigDecimal getReportingScore() {
        return reportingScore;
    }

    public void setReportingScore(BigDecimal reportingScore) {
        this.reportingScore = reportingScore;
    }

    public String getReportingRemarks() {
        return reportingRemarks;
    }

    public void setReportingRemarks(String reportingRemarks) {
        this.reportingRemarks = reportingRemarks;
    }

    public BigDecimal getReviewingScore() {
        return reviewingScore;
    }

    public void setReviewingScore(BigDecimal reviewingScore) {
        this.reviewingScore = reviewingScore;
    }

    public String getReviewingRemarks() {
        return reviewingRemarks;
    }

    public void setReviewingRemarks(String reviewingRemarks) {
        this.reviewingRemarks = reviewingRemarks;
    }

    public BigDecimal getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(BigDecimal finalScore) {
        this.finalScore = finalScore;
    }

    public FinalGrading getFinalGrading() {
        return finalGrading;
    }

    public void setFinalGrading(FinalGrading finalGrading) {
        this.finalGrading = finalGrading;
    }

    public String getRepresentationText() {
        return representationText;
    }

    public void setRepresentationText(String representationText) {
        this.representationText = representationText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeApar";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("aparCycleId", aparCycle != null ? aparCycle.getId() : null);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("reportingOfficerId", reportingOfficer != null ? reportingOfficer.getId() : null);
        snapshot.put("reviewingOfficerId", reviewingOfficer != null ? reviewingOfficer.getId() : null);
        snapshot.put("acceptingAuthorityId", acceptingAuthority != null ? acceptingAuthority.getId() : null);
        snapshot.put("status", status);
        snapshot.put("reportingScore", reportingScore);
        snapshot.put("reviewingScore", reviewingScore);
        snapshot.put("finalScore", finalScore);
        snapshot.put("finalGrading", finalGrading);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
