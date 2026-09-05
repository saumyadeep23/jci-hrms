package in.gov.jci.hrms.exception;

public class OnboardingDraftNotFoundException extends RuntimeException {

    public OnboardingDraftNotFoundException(Long id) {
        super("Onboarding draft not found with id " + id);
    }
}
