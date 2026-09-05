package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EncashmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** POST /api/v1/self-service/leave/encashment - employeeId comes from the request body, matching LeaveApplicationRequest's convention (not resolved from the JWT). */
public record LeaveEncashmentRequest(
        @NotNull Long employeeId,
        @NotNull EncashmentType encashmentType,
        @NotNull @PositiveOrZero BigDecimal elDaysClaimed,
        @PositiveOrZero BigDecimal hplDaysClaimed
) {
}
