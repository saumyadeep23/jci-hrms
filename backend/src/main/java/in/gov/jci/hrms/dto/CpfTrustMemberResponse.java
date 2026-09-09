package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/**
 * One row of the CPF Trust Members' List - GET /api/v1/payroll/trust/members. cpfAcNo/uanNo are the
 * primary identifiers this list is keyed by; everything from separationDate onward exists to surface the
 * gap this module is built to catch: CPF settlement often happens well after an employee's actual
 * separation date (settlementLagDays is null until both dates are known, and negative/zero would flag a
 * data problem rather than a real lag).
 */
public record CpfTrustMemberResponse(
        Long employeeId,
        String employeeCode,
        String fullName,
        String cpfAcNo,
        String uanNo,
        String status,
        String departmentName,
        String designationName,
        LocalDate separationDate,
        String cpfSettlementStatus,
        LocalDate cpfSettlementDate,
        Long settlementLagDays
) {
}
