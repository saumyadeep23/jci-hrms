package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsSettlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record JciEccsSettlementResponse(
        Long id,
        String membershipCode,
        Long employeeId,
        Long exitClearanceRequestId,
        String exitClearanceItemStatus,
        String separationType,
        LocalDate separationDate,
        BigDecimal shareBalance,
        BigDecimal fundBalance,
        BigDecimal securityBalance,
        BigDecimal thriftBalance,
        BigDecimal termPrincipalOutstanding,
        BigDecimal termInterestOutstanding,
        BigDecimal emergencyPrincipalOutstanding,
        BigDecimal emergencyInterestOutstanding,
        BigDecimal otherDues,
        BigDecimal setoffAmount,
        BigDecimal netLiability,
        boolean stale,
        Instant calculatedAt,
        String remarks
) {
    public static JciEccsSettlementResponse from(JciEccsSettlement s) {
        return new JciEccsSettlementResponse(s.getId(), s.getMember().getMembershipCode(), s.getEmployeeId(),
                s.getExitClearanceRequest() != null ? s.getExitClearanceRequest().getId() : null,
                s.getExitClearanceItem() != null ? s.getExitClearanceItem().getStatus().name() : null,
                s.getSeparationType(), s.getSeparationDate(), s.getShareBalance(), s.getFundBalance(), s.getSecurityBalance(),
                s.getThriftBalance(), s.getTermPrincipalOutstanding(), s.getTermInterestOutstanding(), s.getEmergencyPrincipalOutstanding(),
                s.getEmergencyInterestOutstanding(), s.getOtherDues(), s.getSetoffAmount(), s.getNetLiability(), s.isStale(),
                s.getCalculatedAt(), s.getRemarks());
    }
}
