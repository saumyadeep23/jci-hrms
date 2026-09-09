package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.RegularizationType;
import in.gov.jci.hrms.entity.SuspensionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SuspendedStaffReportDto(
        Long employeeId,
        String empCode,
        String employeeName,
        String cadre,
        String designation,
        String hqStation,
        String suspensionOrderNo,
        LocalDate suspensionOrderDate,
        LocalDate effectiveFrom,
        long daysUnderSuspension,
        BigDecimal currentSubsistencePercentage,
        /** true if daysUnderSuspension > 90 and reviewDate IS NULL. */
        boolean isReviewOverdue,
        /** 'VERIFIED' | 'PENDING_VERIFICATION' | 'NOT_SUBMITTED', for the current payroll month. */
        String currentMonthNecStatus,
        SuspensionStatus status,
        RegularizationType regularizationType
) {
}
