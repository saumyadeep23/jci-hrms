package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JciEccsMemberResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String memberName,
        String placeOfPosting,
        String membershipCode,
        LocalDate membershipDate,
        JciEccsMembershipStatus membershipStatus,
        BigDecimal shareBalance,
        BigDecimal fundBalance,
        BigDecimal securityBalance,
        BigDecimal thriftMonthlyAmount
) {
    /**
     * employee may be null only if jcieccs_member.employee_id no longer resolves to a real employee row
     * (should not happen in practice - employee_id is never populated except from a real Employee at
     * creation/migration time) - name/posting fall back to null rather than throwing, so one bad row
     * never breaks the whole directory listing.
     */
    public static JciEccsMemberResponse from(JciEccsMember m, Employee employee) {
        String placeOfPosting = null;
        if (employee != null) {
            placeOfPosting = employee.getDepartmentalPurchaseCentre() != null ? employee.getDepartmentalPurchaseCentre().getName()
                    : employee.getRegionalOffice() != null ? employee.getRegionalOffice().getName() : null;
        }
        return new JciEccsMemberResponse(m.getId(), m.getEmployeeId(), employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getFullName() : null, placeOfPosting, m.getMembershipCode(), m.getMembershipDate(),
                m.getMembershipStatus(), m.getShareBalance(), m.getFundBalance(), m.getSecurityBalance(), m.getThriftMonthlyAmount());
    }
}
