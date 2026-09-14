package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfDisputeStatus;

/** The small "does this transaction have a dispute, and what's its current status" badge (Part 5.2/40) - never the full dispute record (reviewer-only fields are never exposed here). */
public record CpfDisputeBadgeResponse(Long disputeId, String disputeNumber, CpfDisputeStatus status) {
}
