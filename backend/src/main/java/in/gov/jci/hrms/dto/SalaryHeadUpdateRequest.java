package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SalaryHeadEffectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/payroll/masters/salary-heads/{headCount} - every catalog field except headCount itself (the path-supplied, immutable key) is editable. */
public record SalaryHeadUpdateRequest(
        @NotBlank @Size(max = 150) String description,
        @NotBlank @Size(max = 50) String shortName,
        @NotNull SalaryHeadEffectType effectType,
        @NotNull Boolean isVariable,
        @NotNull @Pattern(regexp = "REGULAR|CASUAL|BOTH", message = "must be REGULAR, CASUAL or BOTH") String applicableFor,
        @NotNull Integer salSlipVis,
        @NotNull Boolean basicDependent,
        @NotBlank String refAccountCode
) {
}
