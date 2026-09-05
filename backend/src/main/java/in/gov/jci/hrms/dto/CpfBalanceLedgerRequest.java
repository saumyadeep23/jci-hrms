package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CpfBalanceLedgerRequest(
        @NotNull Long employeeId,
        @NotNull @DecimalMin("0.0") BigDecimal employeeFundBalance,
        @NotNull @DecimalMin("0.0") BigDecimal employerFundBalance,
        @NotNull @DecimalMin("0.0") BigDecimal vpfBalance
) {
}
