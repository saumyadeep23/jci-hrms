package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PIMS_SPEC.md Step 2 (Dynamic Address / India Post API). employeeId is a path variable, not part of this body. */
public record EmployeeAddressRequest(
        @NotNull AddressType addressType,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 150) String postOffice,
        @Size(max = 150) String policeStation,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$") String pinCode
) {
}
