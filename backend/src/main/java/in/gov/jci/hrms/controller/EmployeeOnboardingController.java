package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.OnboardingDraftResponse;
import in.gov.jci.hrms.dto.OnboardingDraftUpsertRequest;
import in.gov.jci.hrms.dto.OnboardingSubmitResponse;
import in.gov.jci.hrms.entity.OnboardingStatus;
import in.gov.jci.hrms.service.EmployeeOnboardingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The 8-step Employee Onboarding & Draft Saving workflow - PIMS_SPEC.md
 * Features 3 & 4. HR-initiated only (no self-service path) - an employee
 * doesn't exist yet for most of a draft's life, so there's no principal to
 * scope an isSelf() check to. No @Valid on the draft-upsert body
 * deliberately - "Save as Draft" bypasses mandatory validation; only
 * finalize() enforces the full schema (see EmployeeOnboardingService).
 *
 * Mapped under both /api/v1/onboarding and /api/onboarding - two different
 * PIMS_SPEC.md revisions have documented this feature under each prefix: an
 * earlier one used /api/v1/onboarding/draft (singular) for the upsert
 * endpoint, a later one used /api/onboarding/drafts (plural, unversioned).
 * Both are accepted rather than picking one and breaking whichever callers
 * built against the other.
 */
@RestController
@RequestMapping({"/api/v1/onboarding", "/api/onboarding"})
@PreAuthorize("hasRole('HR_ADMIN')")
public class EmployeeOnboardingController {

    private final EmployeeOnboardingService onboardingService;

    public EmployeeOnboardingController(EmployeeOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping({"/draft", "/drafts"})
    public ResponseEntity<OnboardingDraftResponse> saveDraft(
            @RequestBody OnboardingDraftUpsertRequest request,
            @RequestHeader(value = "X-Acting-User", required = false) String actingUser) {
        boolean isNew = request.draftId() == null;
        OnboardingDraftResponse draft = onboardingService.upsert(request, actingUser);
        return isNew
                ? ResponseEntity.created(URI.create("/api/v1/onboarding/drafts/" + draft.id())).body(draft)
                : ResponseEntity.ok(draft);
    }

    @GetMapping("/drafts/{id}")
    public OnboardingDraftResponse getById(@PathVariable Long id) {
        return onboardingService.getById(id);
    }

    @GetMapping("/drafts")
    public Page<OnboardingDraftResponse> list(@RequestParam(required = false) OnboardingStatus status, Pageable pageable) {
        return onboardingService.list(status, pageable);
    }

    @PostMapping("/drafts/{id}/finalize")
    public OnboardingSubmitResponse finalizeOnboarding(@PathVariable Long id) {
        return onboardingService.finalizeOnboarding(id);
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<Void> deleteDraft(@PathVariable Long id) {
        onboardingService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }
}
