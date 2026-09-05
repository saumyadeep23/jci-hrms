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
import jakarta.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A "post" is a seat in the org structure (distinct from an Employee, who
 * occupies one via PostIncumbency). operational_reporting_post_id and
 * administrative_reporting_post_id are self-referencing FKs used to walk
 * the reporting hierarchy - see SupervisorResolutionService.
 * post_code uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration.
 */
@Entity
@Table(name = "post_master")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class PostMaster implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_code", nullable = false, length = 30)
    private String postCode;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ro_id")
    private RegionalOffice regionalOffice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpc_id")
    private DepartmentalPurchaseCentre departmentalPurchaseCentre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_reporting_post_id")
    private PostMaster operationalReportingPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrative_reporting_post_id")
    private PostMaster administrativeReportingPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepting_authority_post_id")
    private PostMaster acceptingAuthorityPost;

    @Enumerated(EnumType.STRING)
    @Column(name = "vacancy_status", nullable = false, length = 20)
    private VacancyStatus vacancyStatus = VacancyStatus.VACANT;

    @Column(name = "is_budgeted", nullable = false)
    private boolean budgeted = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Not persisted - PostMasterService.updateStatus() stashes the caller's
     * audit remark here just before saving, purely so it rides along in the
     * generic audit_logs row that AuditableEntityListener records for the
     * status-change UPDATE (there's no dedicated remarks column/table for
     * post status changes).
     */
    @Transient
    private String statusChangeRemark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected PostMaster() {
    }

    public PostMaster(String postCode, String title, Department department, Designation designation, boolean active) {
        this.postCode = postCode;
        this.title = title;
        this.department = department;
        this.designation = designation;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public RegionalOffice getRegionalOffice() {
        return regionalOffice;
    }

    public void setRegionalOffice(RegionalOffice regionalOffice) {
        this.regionalOffice = regionalOffice;
    }

    public DepartmentalPurchaseCentre getDepartmentalPurchaseCentre() {
        return departmentalPurchaseCentre;
    }

    public void setDepartmentalPurchaseCentre(DepartmentalPurchaseCentre departmentalPurchaseCentre) {
        this.departmentalPurchaseCentre = departmentalPurchaseCentre;
    }

    public PostMaster getOperationalReportingPost() {
        return operationalReportingPost;
    }

    public void setOperationalReportingPost(PostMaster operationalReportingPost) {
        this.operationalReportingPost = operationalReportingPost;
    }

    public PostMaster getAdministrativeReportingPost() {
        return administrativeReportingPost;
    }

    public void setAdministrativeReportingPost(PostMaster administrativeReportingPost) {
        this.administrativeReportingPost = administrativeReportingPost;
    }

    public PostMaster getAcceptingAuthorityPost() {
        return acceptingAuthorityPost;
    }

    public void setAcceptingAuthorityPost(PostMaster acceptingAuthorityPost) {
        this.acceptingAuthorityPost = acceptingAuthorityPost;
    }

    public VacancyStatus getVacancyStatus() {
        return vacancyStatus;
    }

    public void setVacancyStatus(VacancyStatus vacancyStatus) {
        this.vacancyStatus = vacancyStatus;
    }

    public boolean isBudgeted() {
        return budgeted;
    }

    public void setBudgeted(boolean budgeted) {
        this.budgeted = budgeted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getStatusChangeRemark() {
        return statusChangeRemark;
    }

    public void setStatusChangeRemark(String statusChangeRemark) {
        this.statusChangeRemark = statusChangeRemark;
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
        return "PostMaster";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("postCode", postCode);
        snapshot.put("title", title);
        snapshot.put("departmentId", department != null ? department.getId() : null);
        snapshot.put("designationId", designation != null ? designation.getId() : null);
        snapshot.put("roId", regionalOffice != null ? regionalOffice.getId() : null);
        snapshot.put("dpcId", departmentalPurchaseCentre != null ? departmentalPurchaseCentre.getId() : null);
        snapshot.put("operationalReportingPostId", operationalReportingPost != null ? operationalReportingPost.getId() : null);
        snapshot.put("administrativeReportingPostId", administrativeReportingPost != null ? administrativeReportingPost.getId() : null);
        snapshot.put("acceptingAuthorityPostId", acceptingAuthorityPost != null ? acceptingAuthorityPost.getId() : null);
        snapshot.put("vacancyStatus", vacancyStatus);
        snapshot.put("budgeted", budgeted);
        snapshot.put("active", active);
        if (statusChangeRemark != null) {
            snapshot.put("statusChangeRemark", statusChangeRemark);
        }
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
