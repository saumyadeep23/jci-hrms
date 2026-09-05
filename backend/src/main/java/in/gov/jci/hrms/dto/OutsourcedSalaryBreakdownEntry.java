package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SalaryBreakdownHeadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OutsourcedSalaryBreakdownEntry(
        @NotBlank @Size(max = 20) String headCode,
        @NotBlank @Size(max = 100) String headName,
        @NotNull SalaryBreakdownHeadType headType,
        @NotNull BigDecimal amount
) {
}
