package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * ESS joining-report submission. Deliberately carries no joining date/session/db-timestamp field -
 * JoiningReportService captures those itself from the database clock (clock_timestamp()), never
 * from the client, per this feature's Database-Clock FN/AN evaluation requirement. Coordinates are
 * "soft GPS" - optional, best-effort (desktop web browsers without a GPS chip still get a
 * network/IP-based estimate, or none at all), never a hard gate on submission.
 */
public record JoiningReportRequest(
        @NotBlank @Size(max = 100) String joiningReportNo,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        BigDecimal accuracyMeters,
        @Size(max = 1000) String remarks
) {
}
