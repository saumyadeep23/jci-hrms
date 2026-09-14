package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfInterestRunScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * POST /api/v1/payroll/trust/interest/runs/calculate - CpfInterestRunService.calculatePreview(). Produces
 * a read-only preview AND persists a CALCULATED run row (Part 5/16 of the module spec: a calculation must
 * show up in the Interest Run register immediately, before anyone approves/posts it); no ledger row is
 * written until a separate POST .../post call. employeeId is required (and must be null otherwise) when
 * scope is SELECTED_MEMBER - CpfInterestRunService enforces this rather than the DTO, so the error message
 * can name the actual FY/scope combination.
 */
public record CpfInterestCalculateRequest(
        @NotNull @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$") String finYear,
        @NotNull CpfInterestRunScope scope,
        Long employeeId,
        @NotBlank String interestOrderNo,
        @NotNull LocalDate interestOrderDate
) {
}
