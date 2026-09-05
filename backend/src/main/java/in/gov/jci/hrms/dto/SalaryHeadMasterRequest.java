package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HeadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalaryHeadMasterRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull HeadType headType,
        @Size(max = 20) String glCode,
        @NotNull Boolean isVariable,
        @NotNull Boolean isTaxable,
        @NotNull Boolean active
) {
}
