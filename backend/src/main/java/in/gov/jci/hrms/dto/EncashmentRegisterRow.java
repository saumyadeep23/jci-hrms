package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One dual-approved encashment application in the In-Service Encashment
 * Register - ALMS Reporting Workbench. estimatedAmount is a rough
 * illustrative figure (daysClaimed * regularBasicPay / 30), not an
 * authoritative payroll computation - there is no encashment pay-rate rule
 * defined anywhere in this codebase, and PayrollComputationService is not
 * wired to leave_encashment_application at all.
 */
public record EncashmentRegisterRow(
        Long applicationNumber,
        String employeeCode,
        String employeeName,
        String encashmentType,
        String qualifyingTenure,
        BigDecimal daysClaimed,
        String hrApprovedBy,
        Instant hrApprovedAt,
        String financeApprovedBy,
        Instant financeApprovedAt,
        boolean isPayrollEligible,
        Long serviceBookFolio,
        BigDecimal estimatedAmount
) {
}
