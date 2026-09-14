package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/payroll/trust/interest/runs/{runId}/reverse - CpfInterestRunService.reverseRun(). */
public record CpfInterestReverseRequest(@NotBlank String reason) {
}
