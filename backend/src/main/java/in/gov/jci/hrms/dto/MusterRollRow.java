package in.gov.jci.hrms.dto;

import java.util.Map;

/**
 * One employee's row in the Statutory Muster Roll (Form II) - ALMS
 * Reporting Workbench. dailyPunches keys are day-of-month ("1".."31") mapped
 * to a standardized muster code (P/WO/GH/RH/CL/HD-CL/EL/HPL/COMM/LWP/ABS/TR)
 * - see AlmsReportService.toMusterCode() for the detail_status -> code
 * translation.
 */
public record MusterRollRow(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String designation,
        String officeName,
        String state,
        Map<String, String> dailyPunches,
        int totalCycleDays,
        int presentDays,
        int paidLeaveDays,
        int weeklyOffsAndHolidays,
        int unpaidLwpDays,
        int penaltyDeductionDays,
        int netPayableDays
) {
}
