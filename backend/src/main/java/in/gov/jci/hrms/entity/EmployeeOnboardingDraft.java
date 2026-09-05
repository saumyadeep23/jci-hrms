package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Working document for the 8-step Employee Onboarding & Draft Saving
 * workflow - see EmployeeOnboardingService for the step sequencing and
 * final-submission rules. stepPayloadsJson holds one JSON object per saved
 * step, keyed "1".."7" (step 8, Review & Submit, writes no payload of its
 * own); it's a pre-serialized JSON string (like AuditLog.beforeState/
 * afterState) rather than a mapped object graph, so this entity has no
 * compile-time dependency on the step DTO shapes - EmployeeOnboardingService
 * owns reading/writing it via ObjectMapper.
 */
@Entity
@Table(name = "employee_onboarding_drafts")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeOnboardingDraft implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_code", nullable = false, length = 30)
    private String draftCode;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @Column(name = "max_step_completed", nullable = false)
    private int maxStepCompleted = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OnboardingStatus status = OnboardingStatus.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_payloads", nullable = false)
    private String stepPayloadsJson = "{}";

    @Column(name = "initiated_by", length = 150)
    private String initiatedBy;

    @Column(name = "submitted_employee_id")
    private Long submittedEmployeeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected EmployeeOnboardingDraft() {
    }

    public EmployeeOnboardingDraft(String draftCode, String employeeCode, String initiatedBy) {
        this.draftCode = draftCode;
        this.employeeCode = employeeCode;
        this.initiatedBy = initiatedBy;
    }

    public Long getId() {
        return id;
    }

    public String getDraftCode() {
        return draftCode;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public int getMaxStepCompleted() {
        return maxStepCompleted;
    }

    public void setMaxStepCompleted(int maxStepCompleted) {
        this.maxStepCompleted = maxStepCompleted;
    }

    public OnboardingStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingStatus status) {
        this.status = status;
    }

    public String getStepPayloadsJson() {
        return stepPayloadsJson;
    }

    public void setStepPayloadsJson(String stepPayloadsJson) {
        this.stepPayloadsJson = stepPayloadsJson;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public Long getSubmittedEmployeeId() {
        return submittedEmployeeId;
    }

    public void setSubmittedEmployeeId(Long submittedEmployeeId) {
        this.submittedEmployeeId = submittedEmployeeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeOnboardingDraft";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public java.util.Map<String, Object> auditSnapshot() {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("draftCode", draftCode);
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("currentStep", currentStep);
        snapshot.put("maxStepCompleted", maxStepCompleted);
        snapshot.put("status", status);
        snapshot.put("submittedEmployeeId", submittedEmployeeId);
        return snapshot;
    }
}
