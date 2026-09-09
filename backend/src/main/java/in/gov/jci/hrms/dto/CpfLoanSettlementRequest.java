package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLoanSettlementMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/payroll/trust/loans/{id}/settle-cash - CpfLoanSettlementService.processCashSettlement(). */
public record CpfLoanSettlementRequest(
        @NotNull @PositiveOrZero BigDecimal principalPaid,
        @NotNull @PositiveOrZero BigDecimal interestPaid,
        @NotNull CpfLoanSettlementMode settlementType,
        @NotBlank String instrumentOrChallanNo,
        @NotNull LocalDate instrumentDate,
        @NotNull LocalDate bankRealizationDate,
        @NotBlank String trustBankAccountCode,
        String challanDocRef,
        String remarks
) {
}
