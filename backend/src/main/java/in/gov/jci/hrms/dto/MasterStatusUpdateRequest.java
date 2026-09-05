package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH .../{id}/status - PIMS_SPEC.md Section 1.B/6. */
public record MasterStatusUpdateRequest(@NotNull Boolean active) {
}
