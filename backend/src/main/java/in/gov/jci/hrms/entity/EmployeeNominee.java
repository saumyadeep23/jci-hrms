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

@Entity
@Table(name = "employee_nominees")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeNominee implements Auditable {

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

    @Column(name = "share_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "nominee_for", nullable = false, length = 50)
    private NominationType nomineeFor;

    /**
     * Links back to the Family Register row this nominee was selected from - name/relationship above
     * are still stored (server-derived from this link at save time via
     * EmployeeNomineeService/EmployeeFamilyNomineeCompositeService, never client-typed when a link is
     * present) rather than dropped, so a later edit/removal of the dependent doesn't corrupt a
     * historical nomination record. Nullable for nominees created via the standalone
     * /api/employees/{id}/nominees endpoint without going through the Family Register.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependent_id")
    private EmployeeDependent dependent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EmployeeNominee() {
    }

    public EmployeeNominee(Employee employee, String name, FamilyRelationshipType relationship, BigDecimal sharePercentage, NominationType nomineeFor) {
        this.employee = employee;
        this.name = name;
        this.relationship = relationship;
        this.sharePercentage = sharePercentage;
        this.nomineeFor = nomineeFor;
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

    public BigDecimal getSharePercentage() {
        return sharePercentage;
    }

    public void setSharePercentage(BigDecimal sharePercentage) {
        this.sharePercentage = sharePercentage;
    }

    public NominationType getNomineeFor() {
        return nomineeFor;
    }

    public void setNomineeFor(NominationType nomineeFor) {
        this.nomineeFor = nomineeFor;
    }

    public EmployeeDependent getDependent() {
        return dependent;
    }

    public void setDependent(EmployeeDependent dependent) {
        this.dependent = dependent;
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
        return "EmployeeNominee";
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
        snapshot.put("sharePercentage", sharePercentage);
        snapshot.put("nomineeFor", nomineeFor);
        snapshot.put("dependentId", dependent != null ? dependent.getId() : null);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
