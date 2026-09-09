package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.QuarterAllotmentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * employeeId is a path variable on the owning controller, not part of this body - see
 * EmployeeQuarterAllotmentController. When syncCurrentAddress is true, this address is also written
 * to the employee's PRESENT employee_addresses row - see EmployeeQuarterAllotmentService.
 */
public record QuarterAllotmentRequest(
        @Size(max = 100) String allotmentOrderNo,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String stateCode,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String pincode,
        @NotNull Boolean syncCurrentAddress,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal licenseFee,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal waterCharges,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal electricCharges,
        @NotNull LocalDate allottedFrom,
        LocalDate vacatedOn,
        @NotNull QuarterAllotmentStatus status,
        @Size(max = 255) String remarks
) {
}
