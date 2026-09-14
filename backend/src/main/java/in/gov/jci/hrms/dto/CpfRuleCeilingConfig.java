package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfCeilingOperator;
import in.gov.jci.hrms.entity.CpfCeilingSourceMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CpfRuleCeilingConfig(
        @NotBlank String componentName, @NotNull CpfCeilingSourceMetric sourceMetric, @NotNull CpfCeilingOperator operator,
        @NotNull BigDecimal factorValue, int displayOrder
) {
}
