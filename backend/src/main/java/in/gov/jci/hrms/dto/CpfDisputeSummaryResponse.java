package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfTransactionDispute;

import java.time.Instant;

/** One row of a dispute list - "My Disputes" (self-service) or the admin dispute queue (Part 12). */
public record CpfDisputeSummaryResponse(
        Long id,
        String disputeNumber,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long cpfLedgerTransactionId,
        CpfDisputeCategory disputeCategory,
        CpfDisputeStatus status,
        Instant raisedAt,
        Long assignedToEmployeeId
) {
    public static CpfDisputeSummaryResponse from(CpfTransactionDispute d) {
        return new CpfDisputeSummaryResponse(d.getId(), d.getDisputeNumber(), d.getEmployee().getId(), d.getEmployee().getEmployeeCode(),
                (d.getEmployee().getFirstName() + " " + d.getEmployee().getLastName()).trim(), d.getCpfLedgerTransaction().getId(),
                d.getDisputeCategory(), d.getStatus(), d.getRaisedAt(), d.getAssignedTo() != null ? d.getAssignedTo().getId() : null);
    }
}
