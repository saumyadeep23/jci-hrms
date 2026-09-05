package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveType;

import java.util.Set;

/**
 * The Leave Type Master's view of a leave type - narrower than
 * LeaveTypeResponse (no code/name editing here, this master is scoped to
 * caps/encashability/cadre eligibility) but adds eligibleCategories and the
 * derived isAccumulative flag LeaveTypeResponse doesn't carry.
 * isAccumulative isn't a stored column - it's true exactly when
 * maxAccumulationDays is set, i.e. the leave type has a carry-forward cap at
 * all (EL); a leave type with no cap (CL/HPL/RH/...) simply doesn't
 * accumulate across years in this schema.
 */
public record LeaveTypeMasterResponse(
        Long id,
        String code,
        String name,
        Integer maxAccumulationCap,
        boolean isEncashable,
        boolean isAccumulative,
        boolean active,
        Set<EmploymentCategory> eligibleCategories
) {
    public static LeaveTypeMasterResponse from(LeaveType leaveType) {
        return new LeaveTypeMasterResponse(
                leaveType.getId(),
                leaveType.getCode(),
                leaveType.getName(),
                leaveType.getMaxAccumulationDays(),
                leaveType.isEncashable(),
                leaveType.getMaxAccumulationDays() != null,
                leaveType.isActive(),
                leaveType.getEligibleCategories()
        );
    }
}
