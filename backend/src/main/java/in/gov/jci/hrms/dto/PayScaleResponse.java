package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.ScaleType;

import java.math.BigDecimal;
import java.time.Instant;

public record PayScaleResponse(
        Long id,
        ScaleType scaleType,
        String grade,
        BigDecimal minimumBasic,
        BigDecimal maximumBasic,
        BigDecimal incrementRate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PayScaleResponse from(PayScale payScale) {
        return new PayScaleResponse(
                payScale.getId(),
                payScale.getScaleType(),
                payScale.getGrade(),
                payScale.getMinimumBasic(),
                payScale.getMaximumBasic(),
                payScale.getIncrementRate(),
                payScale.isActive(),
                payScale.getCreatedAt(),
                payScale.getUpdatedAt()
        );
    }
}
