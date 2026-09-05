package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.TransferNature;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates a MovementOrder together with its first EmployeeMovementRecord in one transaction - the
 * common case (one order, one employee). Additional employees can be added to the same order later
 * via a separate record-only create, but that endpoint isn't exposed yet (out of scope for this
 * feature's first cut).
 *
 * <p>Each station (from/to) is exactly one of an office (RegionalOffice, HO/RO) or a DPC - never
 * both, never neither (see isFromStationValid()/isToStationValid()). A DPC resolves to its own
 * parent RO for *OfficeId on save, with *DpcId carried alongside purely for accurate display - see
 * EmployeeMovementRecord.fromDpc/toDpc's javadoc for why every existing office-dependent code path
 * (payroll HRA, geofence, PDFs) is unaffected by this.
 */
public record MovementOrderCreateRequest(
        @NotNull MovementOrderType orderType,
        @NotBlank @Size(max = 100) String orderRefNo,
        @NotNull LocalDate orderDate,
        LocalDate effectiveDate,
        @Size(max = 100) String sanctionedByRole,
        Long signedByEmployeeId,

        @NotNull Long employeeId,
        @NotNull TransferNature transferNature,
        @NotNull Boolean transferBenefitAdmissible,
        @Size(max = 100) String requestApplicationRef,
        @Size(max = 255) String requestReason,

        Long fromOfficeId,
        Long fromDpcId,
        Long fromDepartmentId,
        @NotNull Long fromDesignationId,
        @Size(max = 50) String fromPayScale,
        Long toOfficeId,
        Long toDpcId,
        Long toDepartmentId,
        @NotNull Long toDesignationId,
        @Size(max = 50) String toPayScale,
        /** Overrides the auto-computed (from RegionalOffice lat/long) station distance when given; auto-computed when null. */
        @PositiveOrZero Integer stationDistanceKmOverride,
        BigDecimal promotionalBasicPay,
        @PositiveOrZero Integer probationPeriodMonths,

        /**
         * Optional grade_scale_master link (V48) for PROMOTION/TRANSFER_CUM_PROMOTION orders - when
         * given, MovementOrderService authoritatively computes and records the new basic pay in
         * regular_pay_fixations (V50) itself (DPE 3%-rounded-to-nearest-10 increment, floored at the
         * target scale's minimum, capped at its maximum) rather than trusting promotionalBasicPay,
         * which stays a caller-supplied display/record field on the movement record itself. Null on a
         * TRANSFER, or on a promotion for a non-REGULAR employee (no pay-fixation ledger to update).
         */
        String toScaleCode
) {
    @AssertTrue(message = "Exactly one of fromOfficeId or fromDpcId must be given")
    public boolean isFromStationValid() {
        return (fromOfficeId != null) != (fromDpcId != null);
    }

    @AssertTrue(message = "Exactly one of toOfficeId or toDpcId must be given")
    public boolean isToStationValid() {
        return (toOfficeId != null) != (toDpcId != null);
    }
}
