package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** POST /api/v1/increments/process-batch - applies the computed increment for exactly the given (already-reviewed) employee ids. */
public record IncrementBatchProcessRequest(
        @NotEmpty List<Long> employeeIds,
        @NotNull @Size(max = 100) String orderNumber,
        @NotNull LocalDate orderDate,
        @Size(max = 500) String remarks
) {
}
