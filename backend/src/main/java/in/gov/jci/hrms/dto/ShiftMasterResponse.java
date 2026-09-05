package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ShiftMaster;

import java.time.Instant;
import java.time.LocalTime;

public record ShiftMasterResponse(
        Long id,
        String shiftCode,
        String shiftName,
        LocalTime startTime,
        LocalTime endTime,
        Integer gracePeriodMinutes,
        boolean crossesMidnight,
        Integer fullDayMinutes,
        Integer halfDayMinutes,
        String applicableOfficeType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShiftMasterResponse from(ShiftMaster shift) {
        return new ShiftMasterResponse(
                shift.getId(),
                shift.getShiftCode(),
                shift.getShiftName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getGracePeriodMinutes(),
                shift.isCrossesMidnight(),
                shift.getFullDayMinutes(),
                shift.getHalfDayMinutes(),
                shift.getApplicableOfficeType(),
                shift.isActive(),
                shift.getCreatedAt(),
                shift.getUpdatedAt()
        );
    }
}
