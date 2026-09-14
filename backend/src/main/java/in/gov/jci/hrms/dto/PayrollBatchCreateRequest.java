package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollBatchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** POST /api/v1/payroll/batches request. cycleYear/cycleMonth name the field the way the (older) PayrollRun API already does - mapped internally onto payroll_batches.sal_year/sal_month. */
public record PayrollBatchCreateRequest(
        @NotNull @Min(2000) Integer cycleYear,
        @NotNull @Min(1) @Max(12) Integer cycleMonth,
        PayrollBatchType batchType,
        LocalDate payDate) {
}
