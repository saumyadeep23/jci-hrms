package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PIMS_SPEC.md Section 1.A.7 (Manpower Vendors). vendorCode is deliberately absent -
 * it is system-generated (VendorCodeGeneratorService) on create and never accepted
 * from the client, mirroring EmployeeRequest's omission of employeeCode.
 */
public record VendorMasterRequest(
        @NotBlank @Size(max = 200) String vendorName,
        @Size(max = 200) String tradeName,
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$") String gstin,
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$") String panNumber,
        @Size(max = 50) String epfRegistrationNo,
        @Size(max = 50) String esicRegistrationNo,
        @NotNull LocalDate contractStartDate,
        @NotNull LocalDate contractEndDate,
        @Size(max = 150) String contactPerson,
        @Size(max = 20) String contactPhone,
        @Size(max = 150) String contactEmail,
        String officeAddress,
        BigDecimal serviceChargePercentage,
        @NotNull Boolean active
) {
    /** Normalizes gstin to trimmed uppercase before the @Pattern check above (and the service's duplicate check) ever see it. */
    public VendorMasterRequest {
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
            if (gstin.isEmpty()) {
                gstin = null;
            }
        }
    }

    @AssertTrue(message = "contractEndDate must not be before contractStartDate")
    public boolean isContractDatesValid() {
        return contractStartDate == null || contractEndDate == null || !contractEndDate.isBefore(contractStartDate);
    }
}
