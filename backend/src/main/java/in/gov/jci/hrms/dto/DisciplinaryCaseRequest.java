package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DisciplinaryCaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisciplinaryCaseRequest(
        @NotBlank @Size(max = 100) String caseNumber,
        @NotNull Long employeeId,
        @NotNull DisciplinaryCaseType caseType
) {
}
