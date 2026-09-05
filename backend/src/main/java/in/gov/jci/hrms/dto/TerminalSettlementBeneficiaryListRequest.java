package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** POST /api/v1/settlements/{id}/beneficiaries - replaces the settlement's entire beneficiary list. */
public record TerminalSettlementBeneficiaryListRequest(
        @NotEmpty @Valid List<TerminalSettlementBeneficiaryRequest> beneficiaries
) {
}
