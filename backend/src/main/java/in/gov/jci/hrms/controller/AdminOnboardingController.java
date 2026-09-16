package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.BulkOnboardingPreviewRequest;
import in.gov.jci.hrms.dto.InvitationResponse;
import in.gov.jci.hrms.dto.OnboardingCandidateResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.RbacSecurity;
import in.gov.jci.hrms.security.onboarding.UserOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * ONBOARDING_SECURITY_REQUIREMENTS.md: onboarding authority (invite/resend/revoke/bulk) is
 * restricted to whoever holds a role granting USER_INVITE* - V94 seeds that only to SYSTEM_ADMIN
 * and HR_ADMIN, per instruction 20 ("No other functional role receives onboarding authority
 * merely because it is an ADMIN role").
 */
@RestController
@RequestMapping("/api/admin/onboarding")
public class AdminOnboardingController {

    private final UserOnboardingService onboardingService;
    private final RbacSecurity rbac;

    public AdminOnboardingController(UserOnboardingService onboardingService, RbacSecurity rbac) {
        this.onboardingService = onboardingService;
        this.rbac = rbac;
    }

    @GetMapping("/candidates/{employeeId}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE')")
    public OnboardingCandidateResponse preview(@PathVariable Long employeeId) {
        return new OnboardingCandidateResponse(employeeId, onboardingService.previewCandidate(employeeId));
    }

    @PostMapping("/candidates/bulk")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE')")
    public List<OnboardingCandidateResponse> previewBulk(@Valid @RequestBody BulkOnboardingPreviewRequest request) {
        List<Long> ids = request.employeeIds();
        List<in.gov.jci.hrms.security.onboarding.CandidateStatus> statuses = onboardingService.previewBulk(ids);
        return java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(i -> new OnboardingCandidateResponse(ids.get(i), statuses.get(i)))
                .toList();
    }

    @PostMapping("/invite/{employeeId}")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE')")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long employeeId, Authentication authentication) {
        Long initiatorId = requireCurrentUserId(authentication);
        var invitation = onboardingService.invite(employeeId, initiatorId);
        return ResponseEntity.created(URI.create("/api/admin/onboarding/" + invitation.getId()))
                .body(InvitationResponse.from(invitation));
    }

    @PostMapping("/{invitationId}/resend")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE_RESEND')")
    public InvitationResponse resend(@PathVariable Long invitationId, Authentication authentication) {
        Long initiatorId = requireCurrentUserId(authentication);
        return InvitationResponse.from(onboardingService.resend(invitationId, initiatorId));
    }

    @PostMapping("/{invitationId}/revoke")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE_REVOKE')")
    public ResponseEntity<Void> revoke(@PathVariable Long invitationId, Authentication authentication) {
        Long actorId = requireCurrentUserId(authentication);
        onboardingService.revoke(invitationId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-invite")
    @PreAuthorize("@rbac.hasPermission(authentication, 'USER_INVITE')")
    public List<Object> bulkInvite(@Valid @RequestBody BulkOnboardingPreviewRequest request, Authentication authentication) {
        Long initiatorId = requireCurrentUserId(authentication);
        return request.employeeIds().stream().<Object>map(employeeId -> {
            try {
                return InvitationResponse.from(onboardingService.invite(employeeId, initiatorId));
            } catch (BusinessRuleViolationException e) {
                return new OnboardingCandidateResponse(employeeId, onboardingService.previewCandidate(employeeId));
            }
        }).toList();
    }

    private Long requireCurrentUserId(Authentication authentication) {
        Long userId = rbac.resolveCurrentUserId(authentication);
        if (userId == null) {
            throw new BusinessRuleViolationException("Caller has no application user account - cannot attribute this action");
        }
        return userId;
    }
}
