package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record JciEccsRestructureRequest(@NotNull @Positive Integer tenureMonths, @NotNull LocalDate effectiveDate) {
}
