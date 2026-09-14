package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;

import java.math.BigDecimal;

public record JciEccsLoanScheduleResponse(
        Long id,
        int installmentNo,
        String cycleCode,
        BigDecimal openingPrincipal,
        int principalDue,
        BigDecimal interestDue,
        BigDecimal totalDue,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalPaid,
        BigDecimal principalOutstanding,
        JciEccsScheduleStatus status
) {
    public static JciEccsLoanScheduleResponse from(JciEccsLoanSchedule s) {
        return new JciEccsLoanScheduleResponse(s.getId(), s.getInstallmentNo(), s.getCycle().getCycleCode(), s.getOpeningPrincipal(),
                s.getPrincipalDue(), s.getInterestDue(), s.getTotalDue(), s.getPrincipalPaid(), s.getInterestPaid(), s.getTotalPaid(),
                s.getPrincipalOutstanding(), s.getStatus());
    }
}
