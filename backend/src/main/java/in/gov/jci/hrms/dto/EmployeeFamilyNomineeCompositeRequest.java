package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The Family & Nominees edit tab's single "Save Changes" payload - replaces the old three separate
 * PUT/POST calls (family details, one dependent at a time, one nominee at a time) with one composite
 * request handled atomically by EmployeeFamilyNomineeCompositeService.save(). pfNominees and
 * gratuityNominees are reconciled independently against employee_nominees.nominee_for - a nominee
 * present only in one list never affects the other's soft-delete-what's-missing sync.
 *
 * <p>No longer carries fatherName/motherName/spouseName/spouseDob directly - those were redundant with
 * the Family &amp; Dependent Register (a FATHER/MOTHER/SPOUSE row already captures the same name/DOB), so
 * the top-of-tab inputs for them were removed from the UI. EmployeeFamilyNomineeCompositeService.save()
 * now derives employee_family_details' own fatherName/motherName/spouseName/spouseDob columns straight
 * from the matching rows in {@code dependents} instead.
 */
public record EmployeeFamilyNomineeCompositeRequest(
        @NotNull @Valid List<CompositeDependentEntry> dependents,
        @NotNull @Valid List<CompositeNomineeEntry> pfNominees,
        @NotNull @Valid List<CompositeNomineeEntry> gratuityNominees
) {
}
