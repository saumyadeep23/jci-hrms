package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CeaClaimType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** claimNo is office-assigned, client-supplied (matching MovementOrderCreateRequest.orderRefNo's own convention) - uniqueness enforced by employee_cea_claims' own DB constraint, checked defensively in CeaClaimService too. */
public record CeaClaimSubmitRequest(
        @NotBlank @Size(max = 50) String claimNo,
        @NotNull Long dependentId,
        @NotBlank @Size(max = 9) String academicYear,
        @NotNull CeaClaimType claimType,
        @NotBlank @Size(max = 200) String schoolName,
        @Size(max = 100) String schoolRegNo,
        @NotBlank @Size(max = 20) String standardClass,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo,
        @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal claimedAmount,
        @Size(max = 100) String supportingDocRef
) {
}
