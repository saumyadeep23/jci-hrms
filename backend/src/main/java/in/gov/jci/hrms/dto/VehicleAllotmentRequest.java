package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** employeeId is a path variable on the owning controller, not part of this body. Status always starts ACTIVE - see EmployeeVehicleAllotmentController's surrender endpoint for the only other transition. */
public record VehicleAllotmentRequest(
        @Size(max = 100) String allotmentOrderNo,
        @NotBlank @Size(max = 50) String vehicleRegNo,
        @Size(max = 100) String vehicleMakeModel,
        @NotNull Boolean driverProvided,
        @NotNull Boolean personalUseAllowed,
        @NotNull Boolean deductionApplicable,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal monthlyDeductionAmount,
        @NotNull LocalDate allottedFrom,
        @Size(max = 255) String remarks
) {
}
