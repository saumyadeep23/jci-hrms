package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** id/status are null for a not-yet-saved preview (GET /preview/:employeeId) - populated once generate() persists the row. */
public record TerminalSettlementResponse(
        Long id,
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
        BigDecimal cpfEmployeeBalance,
        BigDecimal cpfEmployerBalance,
        BigDecimal cpfVpfBalance,
        BigDecimal cpfAccruedInterest,
        BigDecimal totalCpfPayable,
        BigDecimal grossTerminalDues,
        BigDecimal totalRecoveriesDeductions,
        BigDecimal netTerminalPayable,
        TerminalSettlementStatus status,
        List<TerminalSettlementBeneficiaryResponse> beneficiaries
) {
    public static TerminalSettlementResponse preview(TerminalSettlementCalculation c) {
        return new TerminalSettlementResponse(
                null, c.employeeId(), c.separationType(), c.separationDate(), c.lastBasicPay(), c.daRatePercentage(),
                c.daAmount(), c.monthlyEmoluments(), c.qualifyingServiceYears(), c.qualifyingServiceMonths(),
                c.roundedQualifyingYears(), c.elBalanceAtRetirement(), c.hplBalanceAtRetirement(), c.elDaysEncashed(),
                c.hplDaysEncashed(), c.leaveEncashmentElAmount(), c.leaveEncashmentHplAmount(), c.totalLeaveEncashment(),
                c.gratuityAmount(), c.isDeathGratuity(), c.cpfEmployeeBalance(), c.cpfEmployerBalance(), c.cpfVpfBalance(),
                c.cpfAccruedInterest(), c.totalCpfPayable(), c.grossTerminalDues(), c.totalRecoveriesDeductions(),
                c.netTerminalPayable(), null, List.of());
    }

    public static TerminalSettlementResponse from(TerminalSettlement s, List<TerminalSettlementBeneficiaryResponse> beneficiaries) {
        // rounded_qualifying_years isn't a persisted column (only the raw years/months are) -
        // recomputed here with the same >=6-months-rounds-up rule TerminalSettlementService used at generation time.
        int roundedYears = s.getQualifyingServiceMonths() >= 6 ? s.getQualifyingServiceYears() + 1 : s.getQualifyingServiceYears();
        return new TerminalSettlementResponse(
                s.getId(), s.getEmployee().getId(), s.getSeparationType(), s.getSeparationDate(), s.getLastBasicPay(),
                s.getDaRatePercentage(), s.getDaAmount(), s.getLastBasicPay().add(s.getDaAmount()),
                s.getQualifyingServiceYears(), s.getQualifyingServiceMonths(), roundedYears,
                s.getElBalanceAtRetirement(), s.getHplBalanceAtRetirement(), s.getElDaysEncashed(), s.getHplDaysEncashed(),
                s.getLeaveEncashmentElAmount(), s.getLeaveEncashmentHplAmount(), s.getTotalLeaveEncashment(),
                s.getGratuityAmount(), s.isDeathGratuity(), s.getCpfEmployeeBalance(), s.getCpfEmployerBalance(),
                s.getCpfVpfBalance(), s.getCpfAccruedInterest(), s.getTotalCpfPayable(), s.getGrossTerminalDues(),
                s.getTotalRecoveriesDeductions(), s.getNetTerminalPayable(), s.getStatus(), beneficiaries);
    }
}
