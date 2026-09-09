package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Month/year-wise sanction history row (LeaveEncashmentService.listHistory()) - only ever built for
 * a finalized (SANCTIONED or REJECTED) application. voucherRefNo is synthesized ("ELE-{id}"), not a
 * persisted field - this schema has no separate finance voucher-numbering system.
 */
public record LeaveEncashmentHistoryResponse(
        Long id,
        String voucherRefNo,
        Long employeeId,
        String employeeCode,
        String fullName,
        String designation,
        BigDecimal elDaysClaimed,
        BigDecimal basicPay,
        BigDecimal daRateApplied,
        BigDecimal grossAmount,
        BigDecimal arrearAmount,
        boolean arrearSettled,
        Instant hrApprovedAt,
        String hrApprovedByName,
        Instant financeApprovedAt,
        String financeApprovedByName,
        String status
) {
    public static LeaveEncashmentHistoryResponse from(LeaveEncashmentApplication entity, BigDecimal basicPay, String status) {
        Employee employee = entity.getEmployee();
        return new LeaveEncashmentHistoryResponse(
                entity.getId(),
                "ELE-" + entity.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                entity.getElDaysClaimed(),
                basicPay,
                entity.getDaRateApplied(),
                entity.getGrossAmount(),
                entity.getArrearAmount(),
                entity.isArrearSettled(),
                entity.getHrApprovedAt(),
                entity.getHrApprovedBy() != null ? entity.getHrApprovedBy().getFullName() : null,
                entity.getFinanceApprovedAt(),
                entity.getFinanceApprovedBy() != null ? entity.getFinanceApprovedBy().getFullName() : null,
                status);
    }
}
