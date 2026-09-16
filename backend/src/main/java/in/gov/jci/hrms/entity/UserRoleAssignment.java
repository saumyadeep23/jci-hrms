package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-oriented: a revocation sets revokedBy/revokedAt rather than deleting the row, so
 * historical attribution survives (RBAC_SECURITY_REQUIREMENTS.md role/scope audit history
 * requirement). "Active" means revokedAt IS NULL.
 */
@Entity
@Table(name = "user_role_assignments")
public class UserRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUser user;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private ScopeType scopeType;

    @Column(name = "scope_value")
    private Long scopeValue;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(ApplicationUser user, String roleCode, ScopeType scopeType, Long scopeValue, Long assignedBy) {
        this.user = user;
        this.roleCode = roleCode;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.assignedBy = assignedBy;
    }

    public Long getId() {
        return id;
    }

    public ApplicationUser getUser() {
        return user;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public Long getScopeValue() {
        return scopeValue;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getReason() {
        return reason;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(Long revokedBy, String reason) {
        this.revokedBy = revokedBy;
        this.revokedAt = Instant.now();
        this.reason = reason;
    }
}
