package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ProcurementAllowanceRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProcurementAllowanceResponse(
        Long id,
        Long designationId,
        String designationTitle,
        BigDecimal monthlyAllowance,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt
) {
    public static ProcurementAllowanceResponse from(ProcurementAllowanceRate rate) {
        return new ProcurementAllowanceResponse(
                rate.getId(),
                rate.getDesignation().getId(),
                rate.getDesignation().getTitle(),
                rate.getMonthlyAllowance(),
                rate.getEffectiveFrom(),
                rate.getEffectiveTo(),
                rate.getCreatedAt());
    }
}
