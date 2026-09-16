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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * The HRMS application identity for an employee (RBAC_SECURITY_REQUIREMENTS.md /
 * ONBOARDING_SECURITY_REQUIREMENTS.md). At most one row per employee (DB-unique employee_id, V94).
 * Authentication credentials may eventually be owned by an external IdP (identityProvider/
 * identitySubject) - this table owns the employee mapping, account lifecycle, and (via
 * UserRoleAssignment) authorization, not the credential itself.
 */
@Entity
@Table(name = "application_users")
public class ApplicationUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private UserAccountStatus status = UserAccountStatus.PENDING_INVITATION;

    @Column(name = "identity_provider", length = 60)
    private String identityProvider;

    @Column(name = "identity_subject", length = 255)
    private String identitySubject;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ApplicationUser() {
    }

    public ApplicationUser(Employee employee, String username) {
        this.employee = employee;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getUsername() {
        return username;
    }

    public UserAccountStatus getStatus() {
        return status;
    }

    public void setStatus(UserAccountStatus status) {
        this.status = status;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }

    public String getIdentitySubject() {
        return identitySubject;
    }

    public void setIdentitySubject(String identitySubject) {
        this.identitySubject = identitySubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
