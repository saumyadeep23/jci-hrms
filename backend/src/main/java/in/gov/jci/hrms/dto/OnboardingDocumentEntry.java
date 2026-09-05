package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Onboarding wizard Step 8 (Document Verification & Final Review) - PIMS_SPEC.md. */
public record OnboardingDocumentEntry(
        @NotNull DocumentCategory documentCategory,
        @NotBlank @Size(max = 200) String documentTitle,
        @NotBlank @Size(max = 500) String fileS3Key,
        @Size(max = 50) String mimeType
) {
}
