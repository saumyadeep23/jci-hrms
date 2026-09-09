package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PtaxSlab;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PtaxSlabResponse(
        Long id,
        String stateCode,
        BigDecimal slabMin,
        BigDecimal slabMax,
        BigDecimal taxAmount,
        Integer specialMonth,
        BigDecimal specialMonthTax,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt
) {
    public static PtaxSlabResponse from(PtaxSlab slab) {
        return new PtaxSlabResponse(
                slab.getId(),
                slab.getStateCode(),
                slab.getSlabMin(),
                slab.getSlabMax(),
                slab.getTaxAmount(),
                slab.getSpecialMonth(),
                slab.getSpecialMonthTax(),
                slab.getEffectiveFrom(),
                slab.getEffectiveTo(),
                slab.getCreatedAt());
    }
}
