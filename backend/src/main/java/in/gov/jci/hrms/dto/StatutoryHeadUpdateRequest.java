package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/v1/payroll/masters/statutory-heads/{statHeadCount} - statHeadCount itself is the
 * path-supplied, immutable catalog key. Field names match the entity/DB naming (description,
 * shortName) rather than the task brief's "pfHeadDescr"/"headShortName" wording, for consistency
 * with SalaryHeadResponse's identically-shaped description/shortName fields.
 */
public record StatutoryHeadUpdateRequest(
        @NotBlank @Size(max = 150) String description,
        @NotBlank @Size(max = 50) String shortName
) {
}
