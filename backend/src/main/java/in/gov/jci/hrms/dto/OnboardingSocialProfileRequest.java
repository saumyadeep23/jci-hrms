package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SocialCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Onboarding wizard Steps 1 & 7's reservation/social-profile fields -
 * optional, not one of the 8 required data-bearing steps (see
 * EmployeeOnboardingService - this group isn't counted in
 * OnboardingDraftResponse.completionPercentage and isn't required by
 * finalize()). employeeId is derived at finalize time, not part of this body.
 */
public record OnboardingSocialProfileRequest(
        @NotNull SocialCategory socialCategory,
        @Size(max = 100) String subCasteCommunity,
        boolean isPwbd,
        @Size(max = 100) String disabilityType,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal disabilityPercentage,
        boolean isExServiceman,
        boolean isSportsQuota
) {
}
