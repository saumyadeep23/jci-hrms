package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.DpcType;

import java.math.BigDecimal;
import java.time.Instant;

public record DpcResponse(
        Long id,
        Long roId,
        String roCode,
        String roName,
        String code,
        String name,
        String district,
        String state,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer geofenceRadiusMeters,
        boolean active,
        String shortName,
        DpcType dpcType,
        String districtCode,
        CityClass cityClass,
        Instant createdAt,
        Instant updatedAt
) {
    public static DpcResponse from(DepartmentalPurchaseCentre dpc) {
        return new DpcResponse(
                dpc.getId(),
                dpc.getRegionalOffice().getId(),
                dpc.getRegionalOffice().getCode(),
                dpc.getRegionalOffice().getName(),
                dpc.getCode(),
                dpc.getName(),
                dpc.getDistrict(),
                dpc.getState(),
                dpc.getLatitude(),
                dpc.getLongitude(),
                dpc.getGeofenceRadiusMeters(),
                dpc.isActive(),
                dpc.getShortName(),
                dpc.getDpcType(),
                dpc.getDistrictCode(),
                dpc.getCityClass(),
                dpc.getCreatedAt(),
                dpc.getUpdatedAt()
        );
    }
}
