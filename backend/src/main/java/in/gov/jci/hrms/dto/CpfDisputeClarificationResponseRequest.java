package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/ess/cpf/disputes/{id}/respond - the employee's reply to a CLARIFICATION_REQUIRED dispute. */
public record CpfDisputeClarificationResponseRequest(@NotBlank @Size(max = 4000) String response) {
}
