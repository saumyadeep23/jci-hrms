package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** GET /api/v1/employees/separated row - see SeparatedEmployeeDirectoryService. */
public record SeparatedEmployeeResponse(
        Long id,
        String employeeCode,
        String cpfAcNo,
        String fullName,
        String separationType,
        LocalDate separationDate,
        String lastDesignation,
        String lastDepartment,
        String lastRo,
        BigDecimal lastBasicPay,
        String lastScaleGrade,
        String pensionSettlementStatus,
        String clearanceStatus,
        String settlementStatus
) {
}
