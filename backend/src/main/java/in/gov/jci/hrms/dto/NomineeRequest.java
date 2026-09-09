package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.NominationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Standalone create/update, kept for backward compatibility outside the Family & Nominees edit tab.
 * dependentId is optional here (unlike the composite endpoint's own nominee entry, which requires
 * it) - when present, EmployeeNomineeService derives name/relationship from that Family Register row
 * server-side instead of trusting these fields; when absent, name/relationship are used as supplied.
 */
public record NomineeRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 150) String name,
        @NotNull FamilyRelationshipType relationship,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal sharePercentage,
        @NotNull NominationType nomineeFor,
        Long dependentId
) {
}
