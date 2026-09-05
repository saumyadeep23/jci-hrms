package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesignationRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 255) String description,
        /** Optional (V48) - see Designation.gradeScale. Null clears any existing assignment. */
        Long gradeScaleId
) {
}
