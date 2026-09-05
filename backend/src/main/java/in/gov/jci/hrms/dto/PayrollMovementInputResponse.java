package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.PayrollMovementInput;

import java.math.BigDecimal;

public record PayrollMovementInputResponse(
        Long id,
        Long movementId,
        Long employeeId,
        String employeeName,
        String employeeCode,
        int payMonth,
        int payYear,
        String releasingOfficeName,
        int releasingOfficeDays,
        String receivingOfficeName,
        int receivingOfficeDays,
        BigDecimal revisedBasicPay,
        CityClass revisedHraTier,
        int transitJtDays,
        int transitLwpDays,
        boolean payrollApplied,
        String lpcNumber,
        String payrollSyncStatus
) {
    public static PayrollMovementInputResponse from(PayrollMovementInput input) {
        return new PayrollMovementInputResponse(
                input.getId(), input.getMovement().getId(), input.getEmployee().getId(),
                input.getEmployee().getFullName(), input.getEmployee().getEmployeeCode(),
                input.getPayMonth(), input.getPayYear(),
                input.getReleasingOffice() != null ? input.getReleasingOffice().getName() : null, input.getReleasingOfficeDays(),
                input.getReceivingOffice() != null ? input.getReceivingOffice().getName() : null, input.getReceivingOfficeDays(),
                input.getRevisedBasicPay(), input.getRevisedHraTier(), input.getTransitJtDays(), input.getTransitLwpDays(),
                input.isPayrollApplied(), input.getMovement().getLpcNumber(), input.getMovement().getPayrollSyncStatus().name());
    }
}
