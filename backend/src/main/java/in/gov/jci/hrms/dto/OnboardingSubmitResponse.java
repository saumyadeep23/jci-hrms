package in.gov.jci.hrms.dto;

public record OnboardingSubmitResponse(
        Long draftId,
        String draftCode,
        Long employeeId,
        String employeeCode
) {
}
