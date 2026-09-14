package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;

import java.math.BigDecimal;

public record JciEccsCollectionDetailResponse(
        Long id,
        Long employeeId,
        Long memberId,
        BigDecimal thriftAmount,
        Long termLoanId,
        int termPrincipal,
        BigDecimal termInterest,
        Long emergencyLoanId,
        int emergencyPrincipal,
        BigDecimal emergencyInterest,
        BigDecimal totalSnapshotAmount,
        JciEccsDebitStatus debitStatus,
        BigDecimal actualDebitedAmount
) {
    public static JciEccsCollectionDetailResponse from(JciEccsCollectionDetail d) {
        return new JciEccsCollectionDetailResponse(d.getId(), d.getEmployeeId(), d.getMember().getId(), d.getThriftAmount(),
                d.getTermLoan() == null ? null : d.getTermLoan().getId(), d.getTermPrincipal(), d.getTermInterest(),
                d.getEmergencyLoan() == null ? null : d.getEmergencyLoan().getId(), d.getEmergencyPrincipal(), d.getEmergencyInterest(),
                d.getTotalSnapshotAmount(), d.getDebitStatus(), d.getActualDebitedAmount());
    }
}
