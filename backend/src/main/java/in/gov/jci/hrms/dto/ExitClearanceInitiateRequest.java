package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SeparationType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExitClearanceInitiateRequest(
        @NotNull Long employeeId,
        @NotNull SeparationType separationType,
        @NotNull LocalDate targetReleaseDate,
        String remarks
) {
}
