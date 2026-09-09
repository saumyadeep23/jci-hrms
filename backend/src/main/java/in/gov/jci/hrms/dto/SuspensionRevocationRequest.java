package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.RegularizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SuspensionRevocationRequest(
        @NotBlank String revocationOrderNo,
        @NotNull LocalDate revocationOrderDate,
        @NotNull LocalDate revocationEffectiveDate,
        @NotNull RegularizationType regularizationType,
        String regularizationRemarks
) {
}
