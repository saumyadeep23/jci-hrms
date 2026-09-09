package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.LspcBorneBy;
import in.gov.jci.hrms.entity.PayOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeputationInitiateRequest(
        @NotNull Long employeeId,
        @NotNull DeputationDirection deputationDirection,
        @NotBlank String organizationName,
        @NotBlank String organizationType,
        @NotBlank String postingStation,
        boolean isSameStation,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo,
        @NotNull PayOption payOption,
        boolean lspcApplicable,
        LspcBorneBy lspcBorneBy,
        BigDecimal lspcMonthlyRate
) {
}
