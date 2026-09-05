package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BloodGroup;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.Salutation;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String cpfAcNo,
        Salutation salutation,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        Gender gender,
        LocalDate dateOfBirth,
        MaritalStatus maritalStatus,
        BloodGroup bloodGroup,
        String nationality,
        String motherTongue,
        String panNumber,
        /** Already masked ("XXXX-XXXX-1234") as stored - see EmployeeService. */
        String aadhaarRefNumber,
        String personalEmail,
        String officialEmail,
        String phone,
        String officialMobile,
        LocalDate dateOfJoining,
        Long departmentId,
        String departmentName,
        Long designationId,
        String designationTitle,
        Long roId,
        String roName,
        Long dpcId,
        String dpcName,
        Long payScaleId,
        String payScaleGrade,
        EmployeeStatus status,
        boolean geofenceExempted,
        /** "HEAD_OFFICE"/"REGIONAL_OFFICE" (no DPC posting) or the posted DPC's own "DPC"/"SUB_DPC" - computed, not stored. */
        String officeType,
        Instant createdAt,
        Instant updatedAt,
        /** Null unless the caller specifically asked for it (see EmployeeService.getById) - a list endpoint would otherwise N+1 query employee_employment_categories per row. Drives frontend gating like the ESS Sidebar's e-Service Book link (REGULAR only). */
        EmploymentCategory employmentCategory,
        /** Null unless the caller specifically asked for it (see EmployeeService.getById) - DB-trigger-maintained (fn_calculate_jci_superannuation_date, V31/V53), not stored on Employee itself; sourced from employee_superannuation_details. Plain ISO like every other date on this DTO - see GradeScaleMasterResponse's javadoc for why a multi-date DTO doesn't mix wire formats. */
        LocalDate superannuationDate,
        /** Null unless the caller resolved the employee's active SUBSTANTIVE post_incumbency row (see EmployeeService.resolveCurrentPost) - not stored on Employee itself, sourced from post_master via post_incumbency. Drives the Edit Employee "Sanctioned Post" pre-selection. */
        Long postId,
        String postCode,
        String postTitle,
        boolean isNpsEligible,
        boolean isEpsEligible,
        boolean isEpsHigherPensionEligible,
        String pranNumber
) {
    public static EmployeeResponse from(Employee employee) {
        return from(employee, null, null, null);
    }

    public static EmployeeResponse from(Employee employee, EmploymentCategory employmentCategory, LocalDate superannuationDate) {
        return from(employee, employmentCategory, superannuationDate, null);
    }

    public static EmployeeResponse from(Employee employee, EmploymentCategory employmentCategory, LocalDate superannuationDate, PostMaster currentPost) {
        RegionalOffice regionalOffice = employee.getRegionalOffice();
        DepartmentalPurchaseCentre dpc = employee.getDepartmentalPurchaseCentre();
        PayScale payScale = employee.getPayScale();

        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getCpfAcNo(),
                employee.getSalutation(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.getFullName(),
                employee.getGender(),
                employee.getDateOfBirth(),
                employee.getMaritalStatus(),
                employee.getBloodGroup(),
                employee.getNationality(),
                employee.getMotherTongue(),
                employee.getPanNumber(),
                employee.getAadhaarRefNumber(),
                employee.getPersonalEmail(),
                employee.getOfficialEmail(),
                employee.getPhone(),
                employee.getOfficialMobile(),
                employee.getDateOfJoining(),
                employee.getDepartment().getId(),
                employee.getDepartment().getName(),
                employee.getDesignation().getId(),
                employee.getDesignation().getTitle(),
                regionalOffice != null ? regionalOffice.getId() : null,
                regionalOffice != null ? regionalOffice.getName() : null,
                dpc != null ? dpc.getId() : null,
                dpc != null ? dpc.getName() : null,
                payScale != null ? payScale.getId() : null,
                payScale != null ? payScale.getGrade() : null,
                employee.getStatus(),
                employee.isGeofenceExempted(),
                resolveOfficeType(employee),
                employee.getCreatedAt(),
                employee.getUpdatedAt(),
                employmentCategory,
                superannuationDate,
                currentPost != null ? currentPost.getId() : null,
                currentPost != null ? currentPost.getPostCode() : null,
                currentPost != null ? currentPost.getTitle() : null,
                employee.isNpsEligible(),
                employee.isEpsEligible(),
                employee.isEpsHigherPensionEligible(),
                employee.getPranNumber()
        );
    }

    /**
     * DPC/SUB_DPC when posted to a DPC; otherwise whatever the posted RO
     * row's own officeType says (HEAD_OFFICE is itself an RO row - e.g. the
     * Kolkata "01" office - not a separate absence-of-RO state); null if
     * neither is set (an incompletely-onboarded record).
     */
    private static String resolveOfficeType(Employee employee) {
        if (employee.getDepartmentalPurchaseCentre() != null) {
            return employee.getDepartmentalPurchaseCentre().getDpcType().name();
        }
        if (employee.getRegionalOffice() != null) {
            return employee.getRegionalOffice().getOfficeType().name();
        }
        return null;
    }
}
