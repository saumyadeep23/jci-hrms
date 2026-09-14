package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST .../resolve, .../reject, .../request-clarification - remarks is required for all three (the
 * decision/clarification message); expectedVersion is the version the reviewer last saw (Part 29
 * optimistic-locking check) - required so two reviewers racing to resolve/reject the same dispute get a
 * clean 409 rather than one silently overwriting the other's decision.
 */
public record CpfDisputeReviewActionRequest(
        @NotBlank @Size(max = 4000) String remarks,
        @NotNull Long expectedVersion
) {
}
