package in.gov.jci.hrms.dto;

public record RejectedRowSummary(
        Long stagingId,
        String identifier,
        String rejectionReason
) {
}
