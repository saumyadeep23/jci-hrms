package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SeparationType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The full statutory breakdown TerminalSettlementService.calculate()
 * produces - shared by the preview endpoint (never persisted) and
 * generate() (persisted verbatim into a TerminalSettlement row), so the
 * two can never disagree on a figure.
 */
public record TerminalSettlementCalculation(
        Long employeeId,
        SeparationType separationType,
        LocalDate separationDate,
        BigDecimal lastBasicPay,
        BigDecimal daRatePercentage,
        BigDecimal daAmount,
        BigDecimal monthlyEmoluments,
        int qualifyingServiceYears,
        int qualifyingServiceMonths,
        int roundedQualifyingYears,
        BigDecimal elBalanceAtRetirement,
        BigDecimal hplBalanceAtRetirement,
        BigDecimal elDaysEncashed,
        BigDecimal hplDaysEncashed,
        BigDecimal leaveEncashmentElAmount,
        BigDecimal leaveEncashmentHplAmount,
        BigDecimal totalLeaveEncashment,
        BigDecimal gratuityAmount,
        boolean isDeathGratuity,
        BigDecimal gratuityCapApplied,
        BigDecimal cpfEmployeeBalance,
        BigDecimal cpfEmployerBalance,
        BigDecimal cpfVpfBalance,
        BigDecimal cpfAccruedInterest,
        BigDecimal totalCpfPayable,
        BigDecimal grossTerminalDues,
        BigDecimal totalRecoveriesDeductions,
        BigDecimal netTerminalPayable
) {
}
