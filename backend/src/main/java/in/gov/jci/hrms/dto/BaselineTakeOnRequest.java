package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/admin/leave/baseline-takeon - opening balance verified against the physical service book, per PostMaster/HR_ADMIN/SUPER_ADMIN. */
public record BaselineTakeOnRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull LocalDate asOnDate,
        @NotNull @PositiveOrZero BigDecimal openingBalance,
        @PositiveOrZero BigDecimal openingEncashableEl,
        @PositiveOrZero BigDecimal openingEnjoyableEl,
        @Size(max = 50) String physicalServiceBookFolio,
        @Size(max = 100) String verificationOrderRef
) {
}
