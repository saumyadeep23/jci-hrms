package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JciEccsStagingMemberRequest(
        @NotBlank String employeeCode,
        @NotBlank String membershipCode,
        @NotNull LocalDate membershipDate,
        BigDecimal shareBalance,
        BigDecimal fundBalance,
        BigDecimal securityBalance,
        BigDecimal thriftMonthlyAmount
) {
}
