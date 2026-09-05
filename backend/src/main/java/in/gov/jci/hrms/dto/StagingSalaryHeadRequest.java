package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StagingSalaryHeadRequest(
        @NotBlank @Size(max = 20) String salaryHeadCode,
        @NotBlank @Size(max = 20) String headCategory,
        @NotNull BigDecimal amount
) {
}
