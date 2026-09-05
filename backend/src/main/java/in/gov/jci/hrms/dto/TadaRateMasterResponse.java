package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.TadaRateMaster;

import java.math.BigDecimal;
import java.time.Instant;

public record TadaRateMasterResponse(
        Long id,
        Long designationId,
        String designationTitle,
        CityClass cityClass,
        BigDecimal roomRentCeiling,
        BigDecimal dailyAllowanceCeiling,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static TadaRateMasterResponse from(TadaRateMaster rate) {
        return new TadaRateMasterResponse(
                rate.getId(),
                rate.getDesignation().getId(),
                rate.getDesignation().getTitle(),
                rate.getCityClass(),
                rate.getRoomRentCeiling(),
                rate.getDailyAllowanceCeiling(),
                rate.isActive(),
                rate.getCreatedAt(),
                rate.getUpdatedAt()
        );
    }
}
