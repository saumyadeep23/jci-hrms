package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** One employee/month row in the Circular 53 Concession Compliance report - ALMS Reporting Workbench. */
public record Circular53ComplianceRow(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String officeName,
        String monthYear,
        long flexGraceCount,
        long concessionLateCount,
        long concessionEarlyCount,
        long thirdStrikeUnregularizedCount,
        BigDecimal penaltyLeaveDebited
) {
}
