package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveLedgerEntryResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        Long leaveTypeId,
        String leaveTypeCode,
        LocalDate entryDate,
        BigDecimal deltaDays,
        String description,
        LeaveLedgerSource source,
        Long relatedDailyAttendanceId,
        Long relatedLeaveApplicationId,
        Instant createdAt
) {
    public static LeaveLedgerEntryResponse from(LeaveLedgerEntry entry) {
        return new LeaveLedgerEntryResponse(
                entry.getId(),
                entry.getEmployee().getId(),
                entry.getEmployee().getEmployeeCode(),
                entry.getLeaveType().getId(),
                entry.getLeaveType().getCode(),
                entry.getEntryDate(),
                entry.getDeltaDays(),
                entry.getDescription(),
                entry.getSource(),
                entry.getRelatedDailyAttendance() != null ? entry.getRelatedDailyAttendance().getId() : null,
                entry.getRelatedLeaveApplication() != null ? entry.getRelatedLeaveApplication().getId() : null,
                entry.getCreatedAt()
        );
    }
}
