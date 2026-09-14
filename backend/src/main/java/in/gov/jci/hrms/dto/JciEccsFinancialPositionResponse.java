package in.gov.jci.hrms.dto;

/** GET /api/jcieccs/members/{employeeId}/financial-position - the member's share/fund/security/thrift
 * position plus whichever Term/Emergency loan is currently ACTIVE, if any. */
public record JciEccsFinancialPositionResponse(
        JciEccsMemberResponse member,
        JciEccsLoanResponse activeTermLoan,
        JciEccsLoanResponse activeEmergencyLoan
) {
}
