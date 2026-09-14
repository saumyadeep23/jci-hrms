package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsReconciliation;
import in.gov.jci.hrms.entity.JciEccsReconciliationReasonCode;
import in.gov.jci.hrms.entity.JciEccsReconciliationResolutionAction;
import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.entity.JciEccsRecoveryComponent;

import java.math.BigDecimal;
import java.time.Instant;

public record JciEccsReconciliationResponse(
        Long id,
        String payrollRunId,
        Long collectionDetailId,
        String membershipCode,
        String memberName,
        Long employeeId,
        Long loanId,
        String loanIssueId,
        JciEccsRecoveryComponent component,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        BigDecimal postedAmount,
        BigDecimal varianceAmount,
        JciEccsReconciliationStatus status,
        JciEccsReconciliationReasonCode reasonCode,
        Instant detectedAt,
        boolean resolved,
        Instant resolvedAt,
        Long resolvedBy,
        JciEccsReconciliationResolutionAction resolutionAction,
        String resolutionRemarks
) {
    public static JciEccsReconciliationResponse from(JciEccsReconciliation r) {
        return new JciEccsReconciliationResponse(r.getId(), r.getPayrollRunId(),
                r.getCollectionDetail() != null ? r.getCollectionDetail().getId() : null,
                r.getMember().getMembershipCode(), null, r.getEmployeeId(),
                r.getLoan() != null ? r.getLoan().getId() : null, r.getLoan() != null ? r.getLoan().getLoanIssueId() : null,
                r.getComponent(), r.getExpectedAmount(), r.getActualAmount(), r.getPostedAmount(), r.getVarianceAmount(),
                r.getStatus(), r.getReasonCode(), r.getDetectedAt(), r.isResolved(), r.getResolvedAt(), r.getResolvedBy(),
                r.getResolutionAction(), r.getResolutionRemarks());
    }
}
