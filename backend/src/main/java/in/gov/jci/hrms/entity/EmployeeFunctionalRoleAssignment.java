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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Appointment of an employee to a FunctionalRoleMaster role (e.g. HOD of a
 * department, CPIO of an office, Zonal Manager of a zone), scoped by
 * whichever jurisdiction the role needs (department / office / zone_code -
 * at most a role's own convention decides which of those apply; none of the
 * three is enforced NOT NULL at the DB level since not every role needs
 * one). This sits alongside PostIncumbency, not inside it - a functional
 * role carries no cadre-headcount weight.
 *
 * Not Auditable: its PK is UUID (matching functional_role_master and this
 * schema's other UUID-keyed masters, state_master/district_master, V19),
 * and Auditable.auditEntityId() is Long-typed - those same UUID-keyed
 * entities don't implement Auditable either, for the same reason.
 */
@Entity
@Table(name = "employee_functional_role_assignment")
public class EmployeeFunctionalRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private FunctionalRoleMaster role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    private RegionalOffice office;

    @Column(name = "zone_code", length = 50)
    private String zoneCode;

    @Column(name = "office_order_ref", nullable = false, length = 100)
    private String officeOrderRef;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_primary_role", nullable = false)
    private boolean primaryRole = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmployeeFunctionalRoleAssignment() {
    }

    public EmployeeFunctionalRoleAssignment(FunctionalRoleMaster role, Employee employee, String officeOrderRef,
                                             LocalDate orderDate, LocalDate validFrom) {
        this.role = role;
        this.employee = employee;
        this.officeOrderRef = officeOrderRef;
        this.orderDate = orderDate;
        this.validFrom = validFrom;
    }

    public UUID getId() {
        return id;
    }

    public FunctionalRoleMaster getRole() {
        return role;
    }

    public void setRole(FunctionalRoleMaster role) {
        this.role = role;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public RegionalOffice getOffice() {
        return office;
    }

    public void setOffice(RegionalOffice office) {
        this.office = office;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public String getOfficeOrderRef() {
        return officeOrderRef;
    }

    public void setOfficeOrderRef(String officeOrderRef) {
        this.officeOrderRef = officeOrderRef;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public boolean isPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(boolean primaryRole) {
        this.primaryRole = primaryRole;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
