package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeputationRepatriationRequest(
        @NotBlank String repatriationOrderNo,
        @NotNull LocalDate repatriationDate,
        String remarks
) {
}
