package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeSocialProfile;
import in.gov.jci.hrms.entity.SocialCategory;

import java.math.BigDecimal;

public record SocialProfileResponse(
        Long id,
        Long employeeId,
        SocialCategory socialCategory,
        String subCasteCommunity,
        boolean isPwbd,
        String disabilityType,
        BigDecimal disabilityPercentage
) {
    public static SocialProfileResponse from(EmployeeSocialProfile profile) {
        return new SocialProfileResponse(
                profile.getId(),
                profile.getEmployee().getId(),
                profile.getSocialCategory(),
                profile.getSubCasteCommunity(),
                profile.isPwbd(),
                profile.getDisabilityType(),
                profile.getDisabilityPercentage()
        );
    }
}
