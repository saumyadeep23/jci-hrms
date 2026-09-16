package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkOnboardingPreviewRequest(@NotEmpty List<Long> employeeIds) {
}
