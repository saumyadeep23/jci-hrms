package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Cadre;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/masters/grade-scales. scaleCode is the resource's stable identifier (used to address it in PUT .../{scaleCode}) - never renamed after creation. */
public record GradeScaleCreateRequest(
        @NotBlank @Size(max = 10) String scaleCode,
        @NotNull Cadre cadre,
        @NotNull @Min(1) @Max(15) Integer hierarchyLevel,
        @NotNull Boolean boardLevel,
        @NotNull @PositiveOrZero BigDecimal minimumBasic,
        @NotNull @PositiveOrZero BigDecimal maximumBasic,
        @PositiveOrZero BigDecimal incrementRate,
        @NotNull LocalDate effectiveDate,
        @PositiveOrZero BigDecimal contractualLumpsum,
        @PositiveOrZero BigDecimal outsourcedCtc,
        @NotNull Boolean active
) {
    @AssertTrue(message = "maximumBasic must not be less than minimumBasic")
    public boolean isBasicRangeValid() {
        return minimumBasic == null || maximumBasic == null || maximumBasic.compareTo(minimumBasic) >= 0;
    }
}
