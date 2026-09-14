package in.gov.jci.hrms.dto;

/**
 * POST /api/v1/payroll/trust/interest/runs/{runId}/post - CpfInterestRunService.postRun(). acknowledgeDataReview
 * must be explicitly true to post a run the calculate step flagged dataReviewRequired (Part 9/31: a member's
 * pre-migration opening-balance basis could not be confirmed) - an admin override, not a silent bypass.
 */
public record CpfInterestPostRequest(boolean acknowledgeDataReview) {
}
