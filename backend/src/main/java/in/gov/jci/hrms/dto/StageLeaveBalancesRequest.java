package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StageLeaveBalancesRequest(
        @NotEmpty List<@Valid StagingLeaveBalanceRequest> rows
) {
}
