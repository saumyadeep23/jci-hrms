package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ExpenseType;
import in.gov.jci.hrms.entity.MedicalClaimItem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MedicalClaimItemResponse(
        Long id,
        ExpenseType expenseType,
        BigDecimal claimedAmount,
        BigDecimal allowedAmount,
        String billNumber,
        LocalDate billDate,
        String remarks
) {
    public static MedicalClaimItemResponse from(MedicalClaimItem item) {
        return new MedicalClaimItemResponse(
                item.getId(),
                item.getExpenseType(),
                item.getClaimedAmount(),
                item.getAllowedAmount(),
                item.getBillNumber(),
                item.getBillDate(),
                item.getRemarks()
        );
    }
}
