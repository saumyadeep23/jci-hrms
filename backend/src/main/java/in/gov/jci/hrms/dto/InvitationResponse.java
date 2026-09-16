package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.InvitationStatus;
import in.gov.jci.hrms.entity.UserInvitation;

import java.time.Instant;

/** Deliberately excludes tokenHash (ONBOARDING_SECURITY_REQUIREMENTS.md - raw token/hash never exposed through an API response). */
public record InvitationResponse(Long id, Long userId, Long employeeId, InvitationStatus status, Instant issuedAt, Instant expiresAt) {

    public static InvitationResponse from(UserInvitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getUser().getId(), invitation.getUser().getEmployee().getId(),
                invitation.getStatus(), invitation.getIssuedAt(), invitation.getExpiresAt());
    }
}
