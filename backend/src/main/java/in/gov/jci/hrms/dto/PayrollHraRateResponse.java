package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollHraRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PayrollHraRateResponse(
        Long id,
        String cityClass,
        BigDecimal ratePercentage,
        BigDecimal minAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String remarks,
        Instant createdAt,
        Instant updatedAt
) {
    public static PayrollHraRateResponse from(PayrollHraRate rate) {
        return new PayrollHraRateResponse(
                rate.getId(),
                rate.getCityClass(),
                rate.getRatePercentage(),
                rate.getMinAmount(),
                rate.getEffectiveFrom(),
                rate.getEffectiveTo(),
                rate.getRemarks(),
                rate.getCreatedAt(),
                rate.getUpdatedAt());
    }
}
