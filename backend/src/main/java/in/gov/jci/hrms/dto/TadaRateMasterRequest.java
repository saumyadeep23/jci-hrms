package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TadaRateMasterRequest(
        @NotNull Long designationId,
        @NotNull CityClass cityClass,
        @NotNull @DecimalMin("0.0") BigDecimal roomRentCeiling,
        @NotNull @DecimalMin("0.0") BigDecimal dailyAllowanceCeiling,
        @NotNull Boolean active
) {
}
