package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

public record CpfRuleHeadConfig(@NotBlank String headCode, boolean eligible, int debitPriority, int recreditPriority) {
}
