package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

public record CpfRuleDocumentConfig(@NotBlank String documentName, boolean mandatory, String allowedMimeTypes, Integer maxSizeKb) {
}
