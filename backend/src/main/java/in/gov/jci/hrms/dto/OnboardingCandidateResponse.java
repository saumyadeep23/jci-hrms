package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.security.onboarding.CandidateStatus;

public record OnboardingCandidateResponse(Long employeeId, CandidateStatus status) {
}
