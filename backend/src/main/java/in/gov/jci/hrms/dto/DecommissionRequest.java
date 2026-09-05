package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DELETE .../{id} body for RO/DPC decommissioning - a reason is mandatory for the audit trail. */
public record DecommissionRequest(@NotBlank @Size(max = 500) String reason) {
}
