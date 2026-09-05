package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CourseType;
import in.gov.jci.hrms.entity.DivisionClass;
import in.gov.jci.hrms.entity.QualificationLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * employeeId is a path variable on the owning controller, not part of this
 * body - see EmployeeQualificationController. Verification (isVerified/
 * verifiedBy/verifiedAt) is a separate HR action (see the /verify endpoint),
 * not settable here.
 */
public record QualificationRequest(
        @NotNull QualificationLevel qualificationLevel,
        @NotBlank @Size(max = 150) String degreeTitle,
        @Size(max = 150) String specialization,
        @NotBlank @Size(max = 200) String boardUniversity,
        @Size(max = 200) String institutionName,
        @NotNull @Min(1950) @Max(2100) Integer passingYear,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percentageCgpa,
        DivisionClass divisionClass,
        @NotNull CourseType courseType,
        boolean highestQualification,
        @Size(max = 500) String certificateDocumentS3Key
) {
}
