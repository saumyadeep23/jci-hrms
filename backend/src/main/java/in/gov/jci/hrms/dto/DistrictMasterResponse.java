package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DistrictMaster;

import java.time.Instant;
import java.util.UUID;

public record DistrictMasterResponse(
        UUID id,
        String districtCode,
        String districtName,
        UUID stateId,
        String stateName,
        boolean active,
        Instant createdAt
) {
    public static DistrictMasterResponse from(DistrictMaster district) {
        return new DistrictMasterResponse(
                district.getId(),
                district.getDistrictCode(),
                district.getDistrictName(),
                district.getState().getId(),
                district.getState().getStateName(),
                district.isActive(),
                district.getCreatedAt()
        );
    }
}
