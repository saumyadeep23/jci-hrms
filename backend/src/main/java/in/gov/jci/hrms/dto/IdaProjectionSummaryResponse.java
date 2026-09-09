package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DaProjectionBatch;
import in.gov.jci.hrms.entity.DaProjectionBatchStatus;
import in.gov.jci.hrms.entity.ScaleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IdaProjectionSummaryResponse(
        Long id,
        String projectionCode,
        ScaleType scaleType,
        BigDecimal oldDaRate,
        BigDecimal newDaRate,
        LocalDate effectiveFrom,
        int expectedDrawalMonth,
        int expectedDrawalYear,
        int retroMonthsCount,
        int totalActiveEmployees,
        BigDecimal totalMonthlyGrossDelta,
        BigDecimal totalMonthlyEmployerCostDelta,
        BigDecimal totalArrearGrossOutgo,
        BigDecimal totalArrearNetOutgo,
        BigDecimal totalEmployerCostOutgo,
        DaProjectionBatchStatus status,
        List<IdaProjectionMonthWiseSummary> monthWiseBreakup
) {
    public static IdaProjectionSummaryResponse from(DaProjectionBatch batch, List<IdaProjectionMonthWiseSummary> monthWiseBreakup) {
        return new IdaProjectionSummaryResponse(
                batch.getId(),
                batch.getProjectionCode(),
                batch.getScaleType(),
                batch.getOldDaRate(),
                batch.getNewDaRate(),
                batch.getEffectiveFrom(),
                batch.getExpectedDrawalMonth(),
                batch.getExpectedDrawalYear(),
                batch.getRetroMonthsCount(),
                batch.getTotalActiveEmployees(),
                batch.getTotalMonthlyGrossDelta(),
                batch.getTotalMonthlyEmployerCostDelta(),
                batch.getTotalArrearGrossOutgo(),
                batch.getTotalArrearNetOutgo(),
                batch.getTotalEmployerCostOutgo(),
                batch.getStatus(),
                monthWiseBreakup
        );
    }
}
