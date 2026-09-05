package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;

import java.math.BigDecimal;
import java.time.Instant;

public record RegionalOfficeResponse(
        Long id,
        String code,
        String name,
        String state,
        CityClass cityClass,
        boolean active,
        OfficeType officeType,
        String addressLine,
        String city,
        String district,
        String districtCode,
        String pinCode,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal geofenceRadiusMeters,
        BigDecimal recreationClubDeduction,
        boolean procurementAllowanceApplicable,
        Instant createdAt,
        Instant updatedAt
) {
    public static RegionalOfficeResponse from(RegionalOffice regionalOffice) {
        return new RegionalOfficeResponse(
                regionalOffice.getId(),
                regionalOffice.getCode(),
                regionalOffice.getName(),
                regionalOffice.getState(),
                regionalOffice.getCityClass(),
                regionalOffice.isActive(),
                regionalOffice.getOfficeType(),
                regionalOffice.getAddressLine(),
                regionalOffice.getCity(),
                regionalOffice.getDistrict(),
                regionalOffice.getDistrictCode(),
                regionalOffice.getPinCode(),
                regionalOffice.getLatitude(),
                regionalOffice.getLongitude(),
                regionalOffice.getGeofenceRadiusMeters(),
                regionalOffice.getRecreationClubDeduction(),
                regionalOffice.isProcurementAllowanceApplicable(),
                regionalOffice.getCreatedAt(),
                regionalOffice.getUpdatedAt()
        );
    }
}
