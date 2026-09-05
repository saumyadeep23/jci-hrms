package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BloodGroup;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.Salutation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Onboarding wizard Step 1 (Personal & Bio-Data) - PIMS_SPEC.md. cpfAcNo is
 * optional here (unlike panNumber) - left blank, EmployeeService.create()
 * auto-assigns the next sequential number at finalize time; HR only fills
 * it in for a genuine legacy PF ledger number.
 */
public record OnboardingPersonalDetailsRequest(
        @NotNull Salutation salutation,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull Gender gender,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull MaritalStatus maritalStatus,
        BloodGroup bloodGroup,
        @NotBlank @Size(max = 50) String nationality,
        @Size(max = 50) String motherTongue,
        @NotBlank @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$") String panNumber,
        @Size(max = 20) String cpfAcNo,
        @Pattern(regexp = "^[0-9]{12}$") String aadhaarNumber,
        @NotBlank @Email @Size(max = 255) String personalEmail,
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@jcimail\\.in$") String officialEmail,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$") String phone,
        @Size(max = 20) String officialMobile
) {
}
