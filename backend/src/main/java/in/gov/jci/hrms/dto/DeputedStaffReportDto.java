package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.DeputationStatus;
import in.gov.jci.hrms.entity.LspcBorneBy;
import in.gov.jci.hrms.entity.PayOption;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeputedStaffReportDto(
        Long employeeId,
        String empCode,
        String employeeName,
        String cadre,
        String designation,
        DeputationDirection deputationDirection,
        String organizationName,
        String organizationType,
        String postingStation,
        boolean isSameStation,
        LocalDate periodFrom,
        LocalDate periodTo,
        LocalDate extensionValidUpTo,
        PayOption payOption,
        BigDecimal deputationAllowanceRate,
        BigDecimal deputationAllowanceCap,
        boolean lspcApplicable,
        LspcBorneBy lspcBorneBy,
        BigDecimal lspcMonthlyRate,
        DeputationStatus status
) {
}
