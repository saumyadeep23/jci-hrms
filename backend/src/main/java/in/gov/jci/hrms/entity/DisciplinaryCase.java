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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * case_number uniqueness is a plain table-level UNIQUE constraint (not the
 * partial-index-on-deleted_at pattern used elsewhere) since a disciplinary
 * case number must never be reissued, soft-deleted or not. Soft-delete here
 * uses an explicit is_deleted flag (not just deleted_at IS NULL, unlike the
 * rest of this schema) - both columns exist per the V13 migration.
 */
@Entity
@Table(name = "disciplinary_cases")
@SQLRestriction("is_deleted = false")
@EntityListeners(AuditableEntityListener.class)
public class DisciplinaryCase implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", nullable = false, length = 100)
    private String caseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 50, columnDefinition = "VARCHAR")
    private DisciplinaryCaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50, columnDefinition = "VARCHAR")
    private DisciplinaryCaseStatus status = DisciplinaryCaseStatus.INITIATED;

    @Column(name = "charge_sheet_date")
    private LocalDate chargeSheetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_officer_id")
    private Employee inquiryOfficer;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", length = 50, columnDefinition = "VARCHAR")
    private PenaltyType penaltyType;

    @Column(name = "penalty_effective_from")
    private LocalDate penaltyEffectiveFrom;

    @Column(name = "penalty_effective_to")
    private LocalDate penaltyEffectiveTo;

    @Column(name = "remarks")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DisciplinaryCase() {
    }

    public DisciplinaryCase(String caseNumber, Employee employee, DisciplinaryCaseType caseType) {
        this.caseNumber = caseNumber;
        this.employee = employee;
        this.caseType = caseType;
    }

    public Long getId() {
        return id;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DisciplinaryCaseType getCaseType() {
        return caseType;
    }

    public DisciplinaryCaseStatus getStatus() {
        return status;
    }

    public void setStatus(DisciplinaryCaseStatus status) {
        this.status = status;
    }

    public LocalDate getChargeSheetDate() {
        return chargeSheetDate;
    }

    public void setChargeSheetDate(LocalDate chargeSheetDate) {
        this.chargeSheetDate = chargeSheetDate;
    }

    public Employee getInquiryOfficer() {
        return inquiryOfficer;
    }

    public void setInquiryOfficer(Employee inquiryOfficer) {
        this.inquiryOfficer = inquiryOfficer;
    }

    public PenaltyType getPenaltyType() {
        return penaltyType;
    }

    public void setPenaltyType(PenaltyType penaltyType) {
        this.penaltyType = penaltyType;
    }

    public LocalDate getPenaltyEffectiveFrom() {
        return penaltyEffectiveFrom;
    }

    public void setPenaltyEffectiveFrom(LocalDate penaltyEffectiveFrom) {
        this.penaltyEffectiveFrom = penaltyEffectiveFrom;
    }

    public LocalDate getPenaltyEffectiveTo() {
        return penaltyEffectiveTo;
    }

    public void setPenaltyEffectiveTo(LocalDate penaltyEffectiveTo) {
        this.penaltyEffectiveTo = penaltyEffectiveTo;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "DisciplinaryCase";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("caseNumber", caseNumber);
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("caseType", caseType);
        snapshot.put("status", status);
        snapshot.put("chargeSheetDate", chargeSheetDate);
        snapshot.put("inquiryOfficerId", inquiryOfficer != null ? inquiryOfficer.getId() : null);
        snapshot.put("penaltyType", penaltyType);
        snapshot.put("penaltyEffectiveFrom", penaltyEffectiveFrom);
        snapshot.put("penaltyEffectiveTo", penaltyEffectiveTo);
        snapshot.put("deleted", deleted);
        return snapshot;
    }
}
