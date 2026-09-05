package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StateMasterRequest(
        @NotBlank @Size(max = 10) String stateCode,
        @NotBlank @Size(max = 100) String stateName,
        @NotNull StateType stateType,
        @NotNull Boolean active
) {
}
