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
 * POST /api/v1/employees/{id}/renew-contract - deactivates the employee's current
 * contractual_engagements row (is_current=false) and inserts this as the new current one (V50).
 * Dates on the wire are strictly dd-MM-yyyy on both this request and RenewalResultResponse, a
 * self-contained convention for this feature only - see GradeScaleCreateRequest's javadoc for why
 * most write-side DTOs elsewhere in this codebase instead take plain ISO.
 */
public record RenewContractRequest(
        @NotNull @PositiveOrZero BigDecimal monthlyLumpsum,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate contractStartDate,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate contractEndDate,
        @NotBlank @Size(max = 100) String approvalRefNo,
        @Size(max = 10) String scaleCode,
        String engagementTerms
) {
    @AssertTrue(message = "contractEndDate must not be before contractStartDate")
    public boolean isDateRangeValid() {
        return contractStartDate == null || contractEndDate == null || !contractEndDate.isBefore(contractStartDate);
    }
}
