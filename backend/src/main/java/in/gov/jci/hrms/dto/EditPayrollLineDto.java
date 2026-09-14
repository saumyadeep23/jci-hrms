package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** POST .../records/{tranId}/edit request - editing headCount BASIC(1) triggers the DA/HRA/CPF/JCPF/P.Tax cascade, see PayrollBatchEditService. */
public record EditPayrollLineDto(@NotNull Integer headCount, @NotNull BigDecimal newAmount, @NotBlank String changeReason) {
}
