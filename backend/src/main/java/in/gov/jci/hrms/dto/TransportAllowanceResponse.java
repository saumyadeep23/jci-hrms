package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.TransportAllowanceRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransportAllowanceResponse(
        Long id,
        Long gradeScaleId,
        String scaleCode,
        String cityClass,
        BigDecimal baseRate,
        LocalDate effectiveFrom,
        Instant createdAt
) {
    public static TransportAllowanceResponse from(TransportAllowanceRate rate) {
        return new TransportAllowanceResponse(
                rate.getId(),
                rate.getGradeScale() != null ? rate.getGradeScale().getId() : null,
                rate.getGradeScale() != null ? rate.getGradeScale().getScaleCode() : null,
                rate.getCityClass(),
                rate.getBaseRate(),
                rate.getEffectiveFrom(),
                rate.getCreatedAt());
    }
}
