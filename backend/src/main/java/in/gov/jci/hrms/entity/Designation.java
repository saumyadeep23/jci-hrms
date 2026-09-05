package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * title uniqueness is enforced by a partial unique index
 * (WHERE deleted_at IS NULL) in the Flyway migration, matching the
 * soft-delete pattern used by Employee.
 */
@Entity
@Table(name = "designations")
@SQLRestriction("deleted_at IS NULL")
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 255)
    private String description;

    /** Optional (V48) - lets AddMovementOrderModal's promotion-eligibility filter compare hierarchy levels. Null until an admin assigns one via the Designations tab. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_scale_id")
    private GradeScaleMaster gradeScale;

    /** "Director" flags this designation for fn_calculate_jci_superannuation_date's 60-year/5-year-tenure rule (V31/V53) instead of the 58-year regular rule - null/anything else is treated as regular. */
    @Column(name = "category_type", length = 50)
    private String categoryType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Designation() {
    }

    public Designation(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public void setGradeScale(GradeScaleMaster gradeScale) {
        this.gradeScale = gradeScale;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
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
}
