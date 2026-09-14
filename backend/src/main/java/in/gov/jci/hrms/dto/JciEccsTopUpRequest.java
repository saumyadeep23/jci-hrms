package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JciEccsTopUpRequest(@NotNull @Positive BigDecimal topUpAmount, @NotNull @Positive Integer tenureMonths,
                                   @NotNull LocalDate effectiveDate) {
}
