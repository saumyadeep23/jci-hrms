package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PunchType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * employeeId is intentionally NOT @NotNull - an ESS mobile punch from the
 * employee's own device typically omits it and lets
 * MobilePunchController.create() derive it from the JWT's employee_id claim
 * instead. A caller (e.g. an HR/admin tool punching on someone's behalf) may
 * still supply it explicitly, in which case that value is used as-is.
 */
public record MobilePunchRequest(
        Long employeeId,
        @NotNull Instant punchTime,
        @NotNull PunchType punchType,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @PositiveOrZero BigDecimal accuracyMeters,
        @Size(max = 100) String deviceId,
        @Size(max = 255) String photoS3Key
) {
}
