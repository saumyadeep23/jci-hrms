package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfTransactionDispute;

import java.time.Instant;

/**
 * Employee-facing dispute view (Part 40: "Do not expose internal reviewer-only information") - deliberately
 * omits reviewerRemarks/assignedTo/assignedAt, which are reviewer-internal notes, not resolution
 * communication. clarificationRequest/employeeResponse/resolutionRemarks ARE shown - Part 40 explicitly
 * lists these as employee-visible.
 */
public record CpfDisputeResponse(
        Long id,
        String disputeNumber,
        Long cpfLedgerTransactionId,
        CpfDisputeCategory disputeCategory,
        CpfDisputeStatus status,
        String employeeRemarks,
        String attachmentOriginalFilename,
        Instant raisedAt,
        String clarificationRequest,
        String employeeResponse,
        String resolutionRemarks,
        Instant resolvedAt,
        Instant rejectedAt,
        Instant withdrawnAt,
        Long version
) {
    public static CpfDisputeResponse from(CpfTransactionDispute d) {
        return new CpfDisputeResponse(d.getId(), d.getDisputeNumber(), d.getCpfLedgerTransaction().getId(), d.getDisputeCategory(),
                d.getStatus(), d.getEmployeeRemarks(), d.getAttachmentOriginalFilename(), d.getRaisedAt(), d.getClarificationRequest(),
                d.getEmployeeResponse(), d.getResolutionRemarks(), d.getResolvedAt(), d.getRejectedAt(), d.getWithdrawnAt(), d.getVersion());
    }
}
