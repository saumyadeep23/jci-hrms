package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmploymentCategory;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;

/** PUT /api/v1/master/leave-types/{id} body - deliberately narrower than LeaveTypeRequest: code/name/annualQuota/active/careerLimitDays stay editable only through the existing /api/leave-types endpoint, this one is scoped to the 3 fields the Leave Type Master UI exposes. */
public record LeaveTypeMasterUpdateRequest(
        @PositiveOrZero Integer maxAccumulationCap,
        @NotNull Boolean isEncashable,
        @NotEmpty Set<EmploymentCategory> eligibleCategories
) {
}
