package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Onboarding wizard Step 7 (Family, Dependents & Nominees) - PIMS_SPEC.md. */
public record OnboardingFamilyStepRequest(
        @NotBlank @Size(max = 150) String fatherName,
        @Size(max = 150) String motherName,
        @Size(max = 150) String spouseName,
        LocalDate spouseDob,
        List<OnboardingDependentEntry> dependents,
        List<OnboardingNomineeEntry> nominees
) {
}
