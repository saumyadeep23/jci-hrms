package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

public record CpfDisputeStartReviewRequest(@NotNull Long expectedVersion) {
}
