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
 * total_service_days is a Postgres GENERATED ALWAYS ... STORED column
 * (to_date - from_date + 1) - read-only here (insertable/updatable false),
 * never set by application code.
 */
@Entity
@Table(name = "employee_past_service_records")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeePastServiceRecord implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "organization_name", nullable = false, length = 200)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 30)
    private PastServiceOrganizationType organizationType;

    @Column(name = "designation_held", nullable = false, length = 150)
    private String designationHeld;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "total_service_days", insertable = false, updatable = false)
    private Integer totalServiceDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_pay_scale_pattern", length = 10)
    private PastServicePayScalePattern lastPayScalePattern;

    @Column(name = "last_drawn_basic", precision = 12, scale = 2)
    private BigDecimal lastDrawnBasic;

    @Column(name = "last_drawn_gross", precision = 12, scale = 2)
    private BigDecimal lastDrawnGross;

    @Column(name = "is_qualifying_for_pension_gratuity", nullable = false)
    private boolean qualifyingForPensionGratuity = false;

    @Column(name = "qualifying_service_order_ref", length = 100)
    private String qualifyingServiceOrderRef;

    @Column(name = "reason_for_leaving", length = 150)
    private String reasonForLeaving;

    @Column(name = "experience_certificate_s3_key", length = 500)
    private String experienceCertificateS3Key;

    @Column(name = "relieving_noc_document_s3_key", length = 500)
    private String relievingNocDocumentS3Key;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verified_by", length = 150)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeePastServiceRecord() {
    }

    public EmployeePastServiceRecord(Employee employee, String organizationName, PastServiceOrganizationType organizationType,
                                      String designationHeld, LocalDate fromDate, LocalDate toDate) {
        this.employee = employee;
        this.organizationName = organizationName;
        this.organizationType = organizationType;
        this.designationHeld = designationHeld;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public PastServiceOrganizationType getOrganizationType() {
        return organizationType;
    }

    public void setOrganizationType(PastServiceOrganizationType organizationType) {
        this.organizationType = organizationType;
    }

    public String getDesignationHeld() {
        return designationHeld;
    }

    public void setDesignationHeld(String designationHeld) {
        this.designationHeld = designationHeld;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Integer getTotalServiceDays() {
        return totalServiceDays;
    }

    public PastServicePayScalePattern getLastPayScalePattern() {
        return lastPayScalePattern;
    }

    public void setLastPayScalePattern(PastServicePayScalePattern lastPayScalePattern) {
        this.lastPayScalePattern = lastPayScalePattern;
    }

    public BigDecimal getLastDrawnBasic() {
        return lastDrawnBasic;
    }

    public void setLastDrawnBasic(BigDecimal lastDrawnBasic) {
        this.lastDrawnBasic = lastDrawnBasic;
    }

    public BigDecimal getLastDrawnGross() {
        return lastDrawnGross;
    }

    public void setLastDrawnGross(BigDecimal lastDrawnGross) {
        this.lastDrawnGross = lastDrawnGross;
    }

    public boolean isQualifyingForPensionGratuity() {
        return qualifyingForPensionGratuity;
    }

    public void setQualifyingForPensionGratuity(boolean qualifyingForPensionGratuity) {
        this.qualifyingForPensionGratuity = qualifyingForPensionGratuity;
    }

    public String getQualifyingServiceOrderRef() {
        return qualifyingServiceOrderRef;
    }

    public void setQualifyingServiceOrderRef(String qualifyingServiceOrderRef) {
        this.qualifyingServiceOrderRef = qualifyingServiceOrderRef;
    }

    public String getReasonForLeaving() {
        return reasonForLeaving;
    }

    public void setReasonForLeaving(String reasonForLeaving) {
        this.reasonForLeaving = reasonForLeaving;
    }

    public String getExperienceCertificateS3Key() {
        return experienceCertificateS3Key;
    }

    public void setExperienceCertificateS3Key(String experienceCertificateS3Key) {
        this.experienceCertificateS3Key = experienceCertificateS3Key;
    }

    public String getRelievingNocDocumentS3Key() {
        return relievingNocDocumentS3Key;
    }

    public void setRelievingNocDocumentS3Key(String relievingNocDocumentS3Key) {
        this.relievingNocDocumentS3Key = relievingNocDocumentS3Key;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void markVerified(String verifiedBy) {
        this.verified = true;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = Instant.now();
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
        return "EmployeePastServiceRecord";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("organizationName", organizationName);
        snapshot.put("organizationType", organizationType);
        snapshot.put("designationHeld", designationHeld);
        snapshot.put("fromDate", fromDate);
        snapshot.put("toDate", toDate);
        snapshot.put("qualifyingForPensionGratuity", qualifyingForPensionGratuity);
        snapshot.put("reasonForLeaving", reasonForLeaving);
        snapshot.put("verified", verified);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
