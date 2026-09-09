package in.gov.jci.hrms.dto;

import java.util.List;

/** family is null when the employee has no employee_family_details row yet (mirrors FamilyDetailsResponse's own get() convention). */
public record EmployeeFamilyNomineeCompositeResponse(
        FamilyDetailsResponse family,
        List<DependentResponse> dependents,
        List<NomineeResponse> pfNominees,
        List<NomineeResponse> gratuityNominees
) {
}
