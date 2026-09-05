package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BeneficiaryType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TerminalSettlementBeneficiaryRequest(
        @NotNull BeneficiaryType beneficiaryType,
        @NotBlank String beneficiaryName,
        @NotBlank String relationship,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "100.00") BigDecimal sharePercentage,
        @NotNull BigDecimal allocatedAmount,
        @NotBlank String bankAccountNo,
        @NotBlank String bankIfsc,
        String bankName,
        String panNumber
) {
}
