package in.gov.jci.hrms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /api/v1/employees/{id}/renew-deployment - deactivates the employee's current
 * outsourced_deployments row (is_current=false) and inserts this as the new current one (V50).
 * See RenewContractRequest's javadoc for why this feature's dates are strictly dd-MM-yyyy on the
 * wire, unlike most write-side DTOs elsewhere in this codebase.
 */
public record RenewDeploymentRequest(
        Long vendorId,
        @NotNull @PositiveOrZero BigDecimal monthlyCtc,
        @PositiveOrZero BigDecimal agencyBillingRate,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate deploymentStartDate,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate deploymentEndDate,
        @NotBlank @Size(max = 100) String workOrderRef,
        @Size(max = 10) String scaleCode
) {
    @AssertTrue(message = "deploymentEndDate must not be before deploymentStartDate")
    public boolean isDateRangeValid() {
        return deploymentStartDate == null || deploymentEndDate == null || !deploymentEndDate.isBefore(deploymentStartDate);
    }
}
