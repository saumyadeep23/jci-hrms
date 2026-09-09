package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One PF or Gratuity nominee row, nested in EmployeeFamilyNomineeCompositeRequest.pfNominees/
 * gratuityNominees. Deliberately carries no name/relationship/dateOfBirth at all - those are always
 * derived server-side from the Family Register row matching dependentClientKey (see
 * CompositeDependentEntry's own javadoc for what that key is), which is what actually eliminates
 * re-entry rather than just hiding it behind a read-only display field. id is null for a new
 * nomination, non-null for an existing one being edited; any existing nominee of this type NOT
 * present in the submitted list is soft-deleted.
 */
public record CompositeNomineeEntry(
        Long id,
        @NotBlank String dependentClientKey,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal sharePercentage
) {
}
