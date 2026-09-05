package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.entity.PastServicePayScalePattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * employeeId is a path variable on the owning controller, not part of this
 * body - see EmployeePastServiceRecordController. Verification (isVerified/
 * verifiedBy/verifiedAt) is a separate HR action (see the /verify endpoint),
 * not settable here.
 */
public record PastServiceRecordRequest(
        @NotBlank @Size(max = 200) String organizationName,
        @NotNull PastServiceOrganizationType organizationType,
        @NotBlank @Size(max = 150) String designationHeld,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        PastServicePayScalePattern lastPayScalePattern,
        @DecimalMin("0.0") BigDecimal lastDrawnBasic,
        @DecimalMin("0.0") BigDecimal lastDrawnGross,
        boolean qualifyingForPensionGratuity,
        @Size(max = 100) String qualifyingServiceOrderRef,
        @Size(max = 150) String reasonForLeaving,
        @Size(max = 500) String experienceCertificateS3Key,
        @Size(max = 500) String relievingNocDocumentS3Key
) {
}
