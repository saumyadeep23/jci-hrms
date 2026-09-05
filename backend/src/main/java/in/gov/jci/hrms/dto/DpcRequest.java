package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DpcType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * code follows the "0001"-"9999" four-digit zero-padded DPC code convention.
 * state/district describe the DPC's own geographical location and are
 * independent of roId's location - a DPC can be supervised by an RO in a
 * different state (e.g. a border-district DPC administered by a neighbouring
 * state's RO), so neither is ever inherited or validated against the parent
 * RO's state/district.
 */
public record DpcRequest(
        @NotNull Long roId,
        @NotBlank @Pattern(regexp = "0(?!000$)[0-9]{3}|[1-9][0-9]{3}", message = "DPC code must be a four-digit zero-padded value from 0001 to 9999") String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String state,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Positive Integer geofenceRadiusMeters,
        @NotNull Boolean active,
        @Size(max = 20) String shortName,
        @NotNull DpcType dpcType,
        @Size(max = 20) String districtCode,
        @NotNull CityClass cityClass
) {
}
