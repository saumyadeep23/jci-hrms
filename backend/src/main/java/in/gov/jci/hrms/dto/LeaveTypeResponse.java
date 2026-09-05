package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveType;

import java.math.BigDecimal;
import java.time.Instant;

public record LeaveTypeResponse(
        Long id,
        String code,
        String name,
        BigDecimal annualQuota,
        Integer maxAccumulationDays,
        boolean isEncashable,
        Integer careerLimitDays,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getCode(),
                leaveType.getName(),
                leaveType.getAnnualQuota(),
                leaveType.getMaxAccumulationDays(),
                leaveType.isEncashable(),
                leaveType.getCareerLimitDays(),
                leaveType.isActive(),
                leaveType.getCreatedAt(),
                leaveType.getUpdatedAt()
        );
    }
}
