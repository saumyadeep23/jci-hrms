package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /api/jcieccs/loans - records an already-sanctioned-and-disbursed JCIECCS loan in one step (no
 * separate sanction/disburse endpoint exists in this module). tenureMonths is optional - when omitted,
 * the product's own max_tenure_months is used.
 */
public record JciEccsLoanCreateRequest(
        @NotBlank String employeeCode,
        @NotNull JciEccsLoanProductCode productCode,
        @NotNull @Positive BigDecimal sanctionedAmount,
        Integer tenureMonths,
        @NotNull LocalDate applicationDate,
        @NotNull LocalDate sanctionDate,
        @NotNull LocalDate disbursementDate
) {
}
