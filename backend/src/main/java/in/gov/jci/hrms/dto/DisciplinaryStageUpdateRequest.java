package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DisciplinaryCaseStatus;
import in.gov.jci.hrms.entity.PenaltyType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Fields beyond targetStatus are validated as required by DisciplinaryService
 * depending on which status is being transitioned to (e.g. chargeSheetDate
 * only matters/required for CHARGE_SHEET_ISSUED) - see its javadoc for the
 * full per-stage requirement table.
 */
public record DisciplinaryStageUpdateRequest(
        @NotNull DisciplinaryCaseStatus targetStatus,
        LocalDate chargeSheetDate,
        Long inquiryOfficerId,
        PenaltyType penaltyType,
        LocalDate penaltyEffectiveFrom,
        LocalDate penaltyEffectiveTo,
        String remarks
) {
}
