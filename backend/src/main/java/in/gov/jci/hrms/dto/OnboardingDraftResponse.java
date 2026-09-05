package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.OnboardingStatus;

import java.time.Instant;
import java.util.List;

public record OnboardingDraftResponse(
        Long id,
        String draftCode,
        String employeeCode,
        Integer currentStep,
        OnboardingStatus status,
        /** Percentage (0-100) of the 7 data steps that have been saved at least once - GET /api/v1/onboarding/drafts. */
        int completionPercentage,
        OnboardingPersonalDetailsRequest personal,
        EmployeeAddressRequest presentAddress,
        EmployeeAddressRequest permanentAddress,
        Boolean permanentSameAsPresent,
        EmployeeBankAccountRequest banking,
        List<QualificationRequest> qualifications,
        List<PastServiceRecordRequest> pastServiceRecords,
        OnboardingEmploymentStepRequest employment,
        OnboardingFamilyStepRequest family,
        List<OnboardingDocumentEntry> documents,
        OnboardingSocialProfileRequest socialProfile,
        Long submittedEmployeeId,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt
) {
}
