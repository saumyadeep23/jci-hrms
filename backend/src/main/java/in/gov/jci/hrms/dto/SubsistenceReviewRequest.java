package in.gov.jci.hrms.dto;

/**
 * CCS(CCA) Rule 10(6)/10(7)-style 90-day review: subsistence is revised to 75% if the delay in
 * concluding proceedings isn't attributable to the employee, or reduced to 25% if it is - the caller
 * states which is the case, rather than supplying an arbitrary percentage.
 */
public record SubsistenceReviewRequest(
        boolean delayAttributableToEmployee,
        String reviewOrderNo,
        String reviewRemarks
) {
}
