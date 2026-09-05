package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ExpenseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MedicalClaimItemRequest(
        @NotNull ExpenseType expenseType,
        @NotNull @DecimalMin("0.01") BigDecimal claimedAmount,
        @NotBlank @Size(max = 100) String billNumber,
        @NotNull LocalDate billDate,
        String remarks
) {
}
