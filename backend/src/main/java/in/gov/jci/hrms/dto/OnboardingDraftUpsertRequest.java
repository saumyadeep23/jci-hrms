package in.gov.jci.hrms.dto;

import java.util.List;

/**
 * POST /api/v1/onboarding/draft (PIMS_SPEC.md Feature 4). One flexible
 * envelope covering all 7 data-bearing steps (Review & Submit, step 8,
 * carries no fields of its own - see finalize()); every group is optional
 * so "Save as Draft" can persist however much of the form is filled in, in
 * any order, without triggering the full-schema validation that only
 * finalize() performs. draftId is null to start a new draft, or an
 * existing draft's id to update it.
 */
public record OnboardingDraftUpsertRequest(
        Long draftId,
        Integer currentStep,
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
        OnboardingSocialProfileRequest socialProfile
) {
}
