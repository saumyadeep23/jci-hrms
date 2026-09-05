package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;

import java.math.BigDecimal;
import java.time.Instant;

public record LeaveEncashmentResponse(
        Long id,
        Long employeeId,
        String employeeCode,
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
        Instant createdAt,
        Instant updatedAt
) {
    public static LeaveEncashmentResponse from(LeaveEncashmentApplication entity) {
        return new LeaveEncashmentResponse(
                entity.getId(),
                entity.getEmployee().getId(),
                entity.getEmployee().getEmployeeCode(),
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
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
