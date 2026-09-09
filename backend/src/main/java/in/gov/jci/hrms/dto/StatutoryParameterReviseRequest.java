package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/payroll/masters/statutory-parameters/{paramKey}/revise - paramKey is a path variable, not part of this body. */
public record StatutoryParameterReviseRequest(
        @NotNull BigDecimal newValue,
        @NotNull LocalDate newEffectiveFrom,
        @Size(max = 255) String remarks
) {
}
