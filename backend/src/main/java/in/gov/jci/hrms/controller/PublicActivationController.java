package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ActivationRequest;
import in.gov.jci.hrms.security.onboarding.UserOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, pre-auth (permitAll in SecurityConfig - see its comment). Never returns anything more
 * specific than SUCCESS/EXPIRED/INVALID (ONBOARDING_SECURITY_REQUIREMENTS.md: never reveal
 * whether an unrelated employee/invitation exists, never echo the token back).
 */
@RestController
@RequestMapping("/api/public/activation")
public class PublicActivationController {

    private final UserOnboardingService onboardingService;

    public PublicActivationController(UserOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public ResponseEntity<String> activate(@Valid @RequestBody ActivationRequest request) {
        UserOnboardingService.ActivationOutcome outcome = onboardingService.activate(request.token());
        return switch (outcome) {
            case SUCCESS -> ResponseEntity.ok("SUCCESS");
            case EXPIRED -> ResponseEntity.status(HttpStatus.GONE).body("EXPIRED");
            case INVALID -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("INVALID");
        };
    }
}
