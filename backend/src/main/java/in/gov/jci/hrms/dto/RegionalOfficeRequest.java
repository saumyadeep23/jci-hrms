package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.OfficeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** code follows the "01"-"99" two-digit zero-padded RO code convention. */
public record RegionalOfficeRequest(
        @NotBlank @Pattern(regexp = "0[1-9]|[1-9][0-9]", message = "RO code must be a two-digit zero-padded value from 01 to 99") String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String state,
        @NotNull CityClass cityClass,
        @NotNull Boolean active,
        @NotNull OfficeType officeType,
        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 20) String districtCode,
        @Size(max = 10) String pinCode,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @PositiveOrZero BigDecimal geofenceRadiusMeters,
        @PositiveOrZero BigDecimal recreationClubDeduction,
        @NotNull Boolean procurementAllowanceApplicable
) {
}
