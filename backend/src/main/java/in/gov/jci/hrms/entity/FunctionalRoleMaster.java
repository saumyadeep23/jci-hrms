package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The fixed catalogue of non-sanctioned concurrent roles (HoD, CISO, CPIO,
 * FAA, BoT Secretary, Hindi Officer, Vigilance Officer, Zonal Manager) an
 * employee can hold on top of their substantive post via
 * EmployeeFunctionalRoleAssignment - seeded in V40, zero cadre headcount
 * impact by design.
 */
@Entity
@Table(name = "functional_role_master")
public class FunctionalRoleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_id")
    private UUID id;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 150)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_category", nullable = false, length = 50, columnDefinition = "VARCHAR")
    private RoleCategory roleCategory;

    @Column(name = "has_financial_delegation", nullable = false)
    private boolean financialDelegation = false;

    @Column(name = "has_administrative_delegation", nullable = false)
    private boolean administrativeDelegation = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FunctionalRoleMaster() {
    }

    public FunctionalRoleMaster(String roleCode, String roleName, RoleCategory roleCategory) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleCategory = roleCategory;
    }

    public UUID getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public RoleCategory getRoleCategory() {
        return roleCategory;
    }

    public void setRoleCategory(RoleCategory roleCategory) {
        this.roleCategory = roleCategory;
    }

    public boolean isFinancialDelegation() {
        return financialDelegation;
    }

    public void setFinancialDelegation(boolean financialDelegation) {
        this.financialDelegation = financialDelegation;
    }

    public boolean isAdministrativeDelegation() {
        return administrativeDelegation;
    }

    public void setAdministrativeDelegation(boolean administrativeDelegation) {
        this.administrativeDelegation = administrativeDelegation;
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
