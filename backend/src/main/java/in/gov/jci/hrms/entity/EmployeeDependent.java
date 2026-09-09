package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
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
import in.gov.jci.hrms.audit.AuditableEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "employee_dependents")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeDependent implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 50)
    private FamilyRelationshipType relationship;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "is_dependent", nullable = false)
    private boolean dependent = true;

    @Column(name = "is_covered_medical", nullable = false)
    private boolean coveredMedical = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Column(name = "is_divyang", nullable = false)
    private boolean divyang = false;

    @Column(name = "disability_percentage", precision = 5, scale = 2)
    private BigDecimal disabilityPercentage;

    @Column(name = "is_multiple_birth_second_delivery", nullable = false)
    private boolean multipleBirthSecondDelivery = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeDependent() {
    }

    public EmployeeDependent(Employee employee, String name, FamilyRelationshipType relationship, boolean dependent, boolean coveredMedical) {
        this.employee = employee;
        this.name = name;
        this.relationship = relationship;
        this.dependent = dependent;
        this.coveredMedical = coveredMedical;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FamilyRelationshipType getRelationship() {
        return relationship;
    }

    public void setRelationship(FamilyRelationshipType relationship) {
        this.relationship = relationship;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public boolean isDependent() {
        return dependent;
    }

    public void setDependent(boolean dependent) {
        this.dependent = dependent;
    }

    public boolean isCoveredMedical() {
        return coveredMedical;
    }

    public void setCoveredMedical(boolean coveredMedical) {
        this.coveredMedical = coveredMedical;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public boolean isDivyang() {
        return divyang;
    }

    public void setDivyang(boolean divyang) {
        this.divyang = divyang;
    }

    public BigDecimal getDisabilityPercentage() {
        return disabilityPercentage;
    }

    public void setDisabilityPercentage(BigDecimal disabilityPercentage) {
        this.disabilityPercentage = disabilityPercentage;
    }

    public boolean isMultipleBirthSecondDelivery() {
        return multipleBirthSecondDelivery;
    }

    public void setMultipleBirthSecondDelivery(boolean multipleBirthSecondDelivery) {
        this.multipleBirthSecondDelivery = multipleBirthSecondDelivery;
    }

    /**
     * Children Education Allowance eligibility badge - null for any relationship other than
     * SON/DAUGHTER (CEA is specifically a children's allowance). Divyang eligibility (age &le; 22)
     * does not require isDependent(); the standard cutoff (age &le; 20) does. A null dateOfBirth
     * (never actually enforced NOT NULL at the DB level) can't have an age computed, so it falls
     * through to INELIGIBLE_OVERAGE rather than throwing.
     */
    public CeaEligibilityStatus computeCeaEligibility() {
        if (relationship != FamilyRelationshipType.SON && relationship != FamilyRelationshipType.DAUGHTER) {
            return null;
        }
        if (dateOfBirth == null) {
            return CeaEligibilityStatus.INELIGIBLE_OVERAGE;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (divyang && age <= 22) {
            return CeaEligibilityStatus.ELIGIBLE_DIVYANG;
        }
        if (dependent && age <= 20) {
            return CeaEligibilityStatus.ELIGIBLE_STANDARD;
        }
        return CeaEligibilityStatus.INELIGIBLE_OVERAGE;
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
        return "EmployeeDependent";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("name", name);
        snapshot.put("relationship", relationship);
        snapshot.put("dateOfBirth", dateOfBirth);
        snapshot.put("isDependent", dependent);
        snapshot.put("isCoveredMedical", coveredMedical);
        snapshot.put("gender", gender);
        snapshot.put("isDivyang", divyang);
        snapshot.put("disabilityPercentage", disabilityPercentage);
        snapshot.put("isMultipleBirthSecondDelivery", multipleBirthSecondDelivery);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
