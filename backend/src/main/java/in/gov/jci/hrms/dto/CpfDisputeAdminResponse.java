package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfTransactionDispute;

import java.time.Instant;

/** Full reviewer-facing dispute view (Part 12) - includes reviewer-internal fields the employee-facing CpfDisputeResponse deliberately omits. */
public record CpfDisputeAdminResponse(
        Long id,
        String disputeNumber,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long cpfLedgerTransactionId,
        CpfDisputeCategory disputeCategory,
        CpfDisputeStatus status,
        String employeeRemarks,
        String attachmentOriginalFilename,
        String attachmentS3Key,
        Long raisedByEmployeeId,
        Instant raisedAt,
        Long assignedToEmployeeId,
        Instant assignedAt,
        String reviewerRemarks,
        String clarificationRequest,
        String employeeResponse,
        String resolutionRemarks,
        Long resolvedByEmployeeId,
        Instant resolvedAt,
        Long rejectedByEmployeeId,
        Instant rejectedAt,
        Instant withdrawnAt,
        Long version
) {
    public static CpfDisputeAdminResponse from(CpfTransactionDispute d) {
        return new CpfDisputeAdminResponse(d.getId(), d.getDisputeNumber(), d.getEmployee().getId(), d.getEmployee().getEmployeeCode(),
                (d.getEmployee().getFirstName() + " " + d.getEmployee().getLastName()).trim(), d.getCpfLedgerTransaction().getId(),
                d.getDisputeCategory(), d.getStatus(), d.getEmployeeRemarks(), d.getAttachmentOriginalFilename(), d.getAttachmentS3Key(),
                d.getRaisedBy().getId(), d.getRaisedAt(), d.getAssignedTo() != null ? d.getAssignedTo().getId() : null, d.getAssignedAt(),
                d.getReviewerRemarks(), d.getClarificationRequest(), d.getEmployeeResponse(), d.getResolutionRemarks(),
                d.getResolvedBy() != null ? d.getResolvedBy().getId() : null, d.getResolvedAt(),
                d.getRejectedBy() != null ? d.getRejectedBy().getId() : null, d.getRejectedAt(), d.getWithdrawnAt(), d.getVersion());
    }
}
