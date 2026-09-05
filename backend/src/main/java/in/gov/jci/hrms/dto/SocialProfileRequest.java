package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SocialCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** employeeId is a path variable on the owning controller - see EmployeeSocialProfileController. Mirrors OnboardingSocialProfileRequest's field set. */
public record SocialProfileRequest(
        @NotNull SocialCategory socialCategory,
        @Size(max = 100) String subCasteCommunity,
        boolean isPwbd,
        @Size(max = 100) String disabilityType,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal disabilityPercentage
) {
}
