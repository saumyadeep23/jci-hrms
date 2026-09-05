package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.VacancyStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/posts/:id/status - PIMS_SPEC.md Section 2.C's "Change
 * Status" action. targetStatus is FROZEN, ABOLISHED, or VACANT/OCCUPIED to
 * "Unfreeze" (PostMasterService.updateStatus() derives which of those two
 * is correct for the post's current occupancy rather than trusting the
 * caller to know it).
 */
public record PostStatusUpdateRequest(
        @NotNull VacancyStatus targetStatus,
        @Size(max = 500) String remark
) {
}
