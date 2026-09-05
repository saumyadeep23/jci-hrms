package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DistrictMasterRequest(
        @NotBlank @Size(max = 10) String districtCode,
        @NotBlank @Size(max = 100) String districtName,
        @NotNull UUID stateId,
        @NotNull Boolean active
) {
}
