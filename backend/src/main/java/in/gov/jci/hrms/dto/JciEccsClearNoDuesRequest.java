package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

public record JciEccsClearNoDuesRequest(@NotBlank String remarks) {
}
