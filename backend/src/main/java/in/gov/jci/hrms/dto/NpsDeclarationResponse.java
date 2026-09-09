package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record NpsDeclarationResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String financialYear,
        BigDecimal npsPercentage,
        LocalDate effectiveFrom,
        String status,
        String remarks,
        Instant createdAt
) {
    public static NpsDeclarationResponse from(EmployeeNpsDeclaration declaration) {
        return new NpsDeclarationResponse(
                declaration.getId(),
                declaration.getEmployee().getId(),
                declaration.getEmployee().getEmployeeCode(),
                declaration.getEmployee().getFullName(),
                declaration.getFinancialYear(),
                declaration.getNpsPercentage(),
                declaration.getEffectiveFrom(),
                declaration.getStatus(),
                declaration.getRemarks(),
                declaration.getCreatedAt());
    }
}
