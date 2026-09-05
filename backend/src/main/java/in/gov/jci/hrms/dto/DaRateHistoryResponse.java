package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.ScaleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DaRateHistoryResponse(
        Long id,
        ScaleType scaleType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal daPercentage,
        boolean active,
        String orderNumber,
        LocalDate orderDate,
        String remarks
) {
    public static DaRateHistoryResponse from(DaRateHistory rate) {
        return new DaRateHistoryResponse(
                rate.getId(),
                rate.getScaleType(),
                rate.getEffectiveFrom(),
                rate.getEffectiveTo(),
                rate.getDaPercentage(),
                rate.isActive(),
                rate.getOrderNumber(),
                rate.getOrderDate(),
                rate.getRemarks()
        );
    }
}
