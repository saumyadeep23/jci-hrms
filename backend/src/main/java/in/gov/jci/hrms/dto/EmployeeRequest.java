package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BloodGroup;
import in.gov.jci.hrms.entity.EmployeeStatus;
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
 * PIMS_SPEC.md Step 1 (Personal & Bio-Data). employeeCode is never
 * client-supplied - EmployeeCodeGeneratorService allocates it. cpfAcNo
 * (the employee's real Contributory Provident Fund account number) IS
 * client-supplied, same as panNumber - see V56 migration for why this is
 * no longer a DB-generated value - but unlike panNumber, it's optional
 * here: EmployeeService.create() auto-assigns the next sequential number
 * (CpfAcNoGeneratorService) when left blank, so HR only needs to supply
 * one for a genuine legacy PF ledger number.
 * Address (Step 2) and banking (Step 3) are separate normalized resources -
 * see EmployeeAddressController/EmployeeBankAccountController.
 */
public record EmployeeRequest(
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
        /** Raw 12-digit Aadhaar as captured from the user - EmployeeService masks it to "XXXX-XXXX-1234" before persisting. */
        @Pattern(regexp = "^[0-9]{12}$") String aadhaarNumber,
        @NotBlank @Email @Size(max = 255) String personalEmail,
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@jcimail\\.in$") String officialEmail,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$") String phone,
        @Size(max = 20) String officialMobile,
        @NotNull LocalDate dateOfJoining,
        @NotNull Long departmentId,
        @NotNull Long designationId,
        Long roId,
        Long dpcId,
        Long payScaleId,
        @NotNull EmployeeStatus status,
        boolean geofenceExempted,
        /**
         * Optional Sanctioned Post (post_master) assignment - null means
         * "no change to the employee's current post assignment" on create,
         * and "release the currently held post with no replacement" on
         * update. See EmployeeService.syncPostAssignment: assigning a post
         * overrides departmentId/designationId/roId to match the post's own
         * values, since the post - not free-text fields - is authoritative
         * once assigned.
         */
        Long postId,
        /** Defaults to true (a REGULAR employee participates in the CPSE Defined Contribution / NPS scheme) when null - see EmployeeService.applyOptionalFields. */
        Boolean isNpsEligible,
        /** Defaults to false (EPS-95/EPFO only applies to employees migrated in from EPS-covered past employment) when null. */
        Boolean isEpsEligible,
        /**
         * Higher pension (joint option on actual Basic+DA, no ceiling) only
         * ever makes sense when EPS itself applies - force-cleared to false
         * server-side whenever isEpsEligible is false/null, rather than
         * rejected, since a client that flips EPS off isn't expected to also
         * remember to clear this. See EmployeeService.applyOptionalFields.
         */
        Boolean isEpsHigherPensionEligible,
        /** NPS Permanent Retirement Account Number (12 digits) - only meaningful when isNpsEligible. */
        @Pattern(regexp = "^[0-9]{12}$") String pranNumber
) {
}
