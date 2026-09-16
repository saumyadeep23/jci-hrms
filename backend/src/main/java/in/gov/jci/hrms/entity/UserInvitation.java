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
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Onboarding invitation (ONBOARDING_SECURITY_REQUIREMENTS.md). tokenHash is the ONLY token
 * representation ever persisted - the raw token is generated, emailed once, and discarded by
 * UserOnboardingService; it is never passed to this entity.
 */
@Entity
@Table(name = "user_invitations")
public class UserInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUser user;

    @Column(name = "initiated_by_user_id", nullable = false)
    private Long initiatedByUserId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private InvitationStatus status = InvitationStatus.INVITED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Optimistic-lock guard: concurrent activate-vs-activate and activate-vs-resend races both fail one side with ObjectOptimisticLockingFailureException (already mapped to 409 by GlobalExceptionHandler). */
    @Version
    @Column(name = "version_no")
    private Long versionNo;

    protected UserInvitation() {
    }

    public UserInvitation(ApplicationUser user, Long initiatedByUserId, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.initiatedByUserId = initiatedByUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public ApplicationUser getUser() {
        return user;
    }

    public Long getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Long getRevokedByUserId() {
        return revokedByUserId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke(Long revokedByUserId) {
        this.status = InvitationStatus.REVOKED;
        this.revokedByUserId = revokedByUserId;
        this.revokedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
