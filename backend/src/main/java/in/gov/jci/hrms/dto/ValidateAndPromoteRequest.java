package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * hrAdminUsername satisfies FR-MIG.9 (HR-Admin authorization) - it is used
 * as the finalizedBy value on any HISTORIC_MIGRATED payroll run created
 * during this promotion pass, which are created directly in FINALIZED
 * status (see LegacyMigrationService), since there is no reopen path in
 * PayrollRunService to guard against otherwise.
 */
public record ValidateAndPromoteRequest(
        LocalDate cutoffDate,
        @NotBlank String hrAdminUsername
) {
}
