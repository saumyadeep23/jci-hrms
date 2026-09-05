package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

@Entity
@Table(name = "employee_qualifications")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeQualification implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Convert(converter = QualificationLevelConverter.class)
    @Column(name = "qualification_level", nullable = false, length = 30)
    private QualificationLevel qualificationLevel;

    @Column(name = "degree_title", nullable = false, length = 150)
    private String degreeTitle;

    @Column(name = "specialization", length = 150)
    private String specialization;

    @Column(name = "board_university", nullable = false, length = 200)
    private String boardUniversity;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "passing_year", nullable = false)
    private Integer passingYear;

    @Column(name = "percentage_cgpa", precision = 5, scale = 2)
    private BigDecimal percentageCgpa;

    @Enumerated(EnumType.STRING)
    @Column(name = "division_class", length = 20)
    private DivisionClass divisionClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType = CourseType.FULL_TIME;

    @Column(name = "is_highest_qualification", nullable = false)
    private boolean highestQualification = false;

    @Column(name = "certificate_document_s3_key", length = 500)
    private String certificateDocumentS3Key;

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

    protected EmployeeQualification() {
    }

    public EmployeeQualification(Employee employee, QualificationLevel qualificationLevel, String degreeTitle,
                                  String boardUniversity, Integer passingYear) {
        this.employee = employee;
        this.qualificationLevel = qualificationLevel;
        this.degreeTitle = degreeTitle;
        this.boardUniversity = boardUniversity;
        this.passingYear = passingYear;
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

    public QualificationLevel getQualificationLevel() {
        return qualificationLevel;
    }

    public void setQualificationLevel(QualificationLevel qualificationLevel) {
        this.qualificationLevel = qualificationLevel;
    }

    public String getDegreeTitle() {
        return degreeTitle;
    }

    public void setDegreeTitle(String degreeTitle) {
        this.degreeTitle = degreeTitle;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getBoardUniversity() {
        return boardUniversity;
    }

    public void setBoardUniversity(String boardUniversity) {
        this.boardUniversity = boardUniversity;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public Integer getPassingYear() {
        return passingYear;
    }

    public void setPassingYear(Integer passingYear) {
        this.passingYear = passingYear;
    }

    public BigDecimal getPercentageCgpa() {
        return percentageCgpa;
    }

    public void setPercentageCgpa(BigDecimal percentageCgpa) {
        this.percentageCgpa = percentageCgpa;
    }

    public DivisionClass getDivisionClass() {
        return divisionClass;
    }

    public void setDivisionClass(DivisionClass divisionClass) {
        this.divisionClass = divisionClass;
    }

    public CourseType getCourseType() {
        return courseType;
    }

    public void setCourseType(CourseType courseType) {
        this.courseType = courseType;
    }

    public boolean isHighestQualification() {
        return highestQualification;
    }

    public void setHighestQualification(boolean highestQualification) {
        this.highestQualification = highestQualification;
    }

    public String getCertificateDocumentS3Key() {
        return certificateDocumentS3Key;
    }

    public void setCertificateDocumentS3Key(String certificateDocumentS3Key) {
        this.certificateDocumentS3Key = certificateDocumentS3Key;
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
        return "EmployeeQualification";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("qualificationLevel", qualificationLevel);
        snapshot.put("degreeTitle", degreeTitle);
        snapshot.put("specialization", specialization);
        snapshot.put("boardUniversity", boardUniversity);
        snapshot.put("institutionName", institutionName);
        snapshot.put("passingYear", passingYear);
        snapshot.put("percentageCgpa", percentageCgpa);
        snapshot.put("divisionClass", divisionClass);
        snapshot.put("courseType", courseType);
        snapshot.put("highestQualification", highestQualification);
        snapshot.put("verified", verified);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
