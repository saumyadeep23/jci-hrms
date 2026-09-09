package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StateType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * remoteAllowancePercentage is always required (never null) - the client sends 0.00 when
 * isRemoteArea is false and a concrete (0.00, 100.00] value when true (the State Master screen
 * pre-fills 10.00 the moment the toggle is switched on). The cross-field rule tying the two together
 * is enforced in StateMasterService, not via annotations alone, since it depends on isRemoteArea's
 * value.
 */
public record StateMasterRequest(
        @NotBlank @Size(min = 2, max = 10) String stateCode,
        @NotBlank @Size(max = 100) String stateName,
        @NotNull StateType stateType,
        @NotNull Boolean active,
        @NotNull Boolean isRemoteArea,
        @NotNull
        @DecimalMin(value = "0.00", message = "must not be negative") @DecimalMax(value = "100.00", message = "must not exceed 100.00")
        BigDecimal remoteAllowancePercentage
) {
}
