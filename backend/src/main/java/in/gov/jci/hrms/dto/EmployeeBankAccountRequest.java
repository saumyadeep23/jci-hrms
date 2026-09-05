package in.gov.jci.hrms.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PIMS_SPEC.md Step 3 (Dynamic Banking & Disbursal). employeeId is a path
 * variable, not part of this body. Inserting one always creates a new
 * ACTIVE/primary-disbursal row - the SCD Type-2 archival of whatever was
 * previously active is handled entirely by the fn_sync_employee_bank_change
 * DB trigger (V30 migration), not application code.
 */
public record EmployeeBankAccountRequest(
        @NotBlank @Size(max = 150) String bankName,
        @NotBlank @Size(max = 150) String bankBranch,
        @NotBlank @Size(max = 35) String bankAccountNumber,
        @NotBlank @Size(max = 35) String reenterBankAccountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$") String bankIfsc,
        @Size(max = 500) String cancelledChequeS3Key
) {
    @AssertTrue(message = "bankAccountNumber and reenterBankAccountNumber must match")
    @JsonIgnore
    public boolean isAccountNumberConfirmed() {
        return bankAccountNumber != null && bankAccountNumber.equals(reenterBankAccountNumber);
    }
}
