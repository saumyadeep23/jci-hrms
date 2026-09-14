package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/** GET /api/v1/payroll/trust/members/{employeeId}/eps-eligibility - CpfEpsEligibilityService.checkEligibility(). */
public record CpfEpsEligibilityResponse(
        Long employeeId,
        String fullName,
        String cpfAcNo,
        String uanNo,
        LocalDate dateOfBirth,
        LocalDate dateOfJoining,
        LocalDate dateOfSeparation,
        boolean epsMember,
        /** Whole-years-and-months rendering of the eligible-service span, e.g. "12 years, 4 months". */
        String eligibleServiceLabel,
        long eligibleServiceDays,
        /** Same span as eligibleService in this codebase today - there is no break-in-service/non-pensionable-period tracking yet to distinguish the two; see the service's own javadoc. */
        String pensionableServiceLabel,
        CpfEpsEligibilityStatus eligibility,
        String eligibilityBasis
) {
}
