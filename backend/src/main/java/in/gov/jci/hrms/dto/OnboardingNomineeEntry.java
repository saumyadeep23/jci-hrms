package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Nominee entry nested in Step 7 - employeeId isn't known yet, unlike the standalone NomineeRequest. */
public record OnboardingNomineeEntry(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String relationship,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal sharePercentage,
        @NotBlank @Size(max = 50) String nomineeFor
) {
}
