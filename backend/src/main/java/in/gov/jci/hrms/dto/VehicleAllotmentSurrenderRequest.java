package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VehicleAllotmentSurrenderRequest(
        @NotNull LocalDate surrenderedOn,
        @Size(max = 255) String remarks
) {
}
