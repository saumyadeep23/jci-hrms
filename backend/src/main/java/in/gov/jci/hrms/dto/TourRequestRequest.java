package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TourRequestRequest(
        @NotBlank @Size(max = 50) String requestNumber,
        @NotNull Long employeeId,
        @NotBlank String purpose,
        @NotBlank @Size(max = 150) String origin,
        @NotBlank @Size(max = 150) String destination,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean isPostFacto,
        @PositiveOrZero BigDecimal directFlightCost,
        @PositiveOrZero BigDecimal directHotelCost,
        @PositiveOrZero BigDecimal directVehicleCost
) {
}
