package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StageLoansRequest(
        @NotEmpty List<@Valid StagingLoanRequest> rows
) {
}
