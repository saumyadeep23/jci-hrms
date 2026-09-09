package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollRun;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveEncashmentResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String fullName,
        String designation,
        /** Null when the caller didn't request the (batched, admin-review-only) basic-pay lookup - see LeaveEncashmentService.listForAdminReview(). */
        BigDecimal currentBasicPay,
        EncashmentType encashmentType,
        BigDecimal elDaysClaimed,
        BigDecimal hplDaysClaimed,
        ApprovalStatus hrApprovalStatus,
        Long hrApprovedByEmployeeId,
        Instant hrApprovedAt,
        String hrRemarks,
        ApprovalStatus financeApprovalStatus,
        Long financeApprovedByEmployeeId,
        Instant financeApprovedAt,
        String financeRemarks,
        boolean payrollEligible,
        Long serviceBookEntryId,
        /** Null for applications submitted before V63 and never finance-approved since (no snapshot was ever taken for them). */
        BigDecimal daRateApplied,
        LocalDate daEffectiveDate,
        BigDecimal grossAmount,
        boolean arrearSettled,
        BigDecimal arrearAmount,
        BigDecimal arrearDaRateDiff,
        /** Non-null once PayrollRunService.compute() has queued the base gross_amount into a run - null means "not yet queued for any payroll run". */
        Integer payrollCycleYear,
        Integer payrollCycleMonth,
        boolean payrollProcessed,
        /** The arrear's own (possibly later) payroll queue - independent of payrollCycleYear/Month above. */
        Integer arrearPayrollCycleYear,
        Integer arrearPayrollCycleMonth,
        /** Same instant as createdAt - kept as a second, more self-explanatory name for admin-review UI consumers. */
        Instant applicationDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeaveEncashmentResponse from(LeaveEncashmentApplication entity) {
        return from(entity, null);
    }

    public static LeaveEncashmentResponse from(LeaveEncashmentApplication entity, BigDecimal currentBasicPay) {
        Employee employee = entity.getEmployee();
        PayrollRun payrollRun = entity.getPayrollRun();
        PayrollRun arrearPayrollRun = entity.getArrearPayrollRun();
        return new LeaveEncashmentResponse(
                entity.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                currentBasicPay,
                entity.getEncashmentType(),
                entity.getElDaysClaimed(),
                entity.getHplDaysClaimed(),
                entity.getHrApprovalStatus(),
                entity.getHrApprovedBy() != null ? entity.getHrApprovedBy().getId() : null,
                entity.getHrApprovedAt(),
                entity.getHrRemarks(),
                entity.getFinanceApprovalStatus(),
                entity.getFinanceApprovedBy() != null ? entity.getFinanceApprovedBy().getId() : null,
                entity.getFinanceApprovedAt(),
                entity.getFinanceRemarks(),
                entity.isPayrollEligible(),
                entity.getServiceBookEntry() != null ? entity.getServiceBookEntry().getId() : null,
                entity.getDaRateApplied(),
                entity.getDaEffectiveDate(),
                entity.getGrossAmount(),
                entity.isArrearSettled(),
                entity.getArrearAmount(),
                entity.getArrearDaRateDiff(),
                payrollRun != null ? payrollRun.getCycleYear() : null,
                payrollRun != null ? payrollRun.getCycleMonth() : null,
                entity.isPayrollProcessed(),
                arrearPayrollRun != null ? arrearPayrollRun.getCycleYear() : null,
                arrearPayrollRun != null ? arrearPayrollRun.getCycleMonth() : null,
                entity.getCreatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
