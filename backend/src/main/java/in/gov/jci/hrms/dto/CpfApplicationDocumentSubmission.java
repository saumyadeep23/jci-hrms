package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * One document the applicant actually submitted against a CpfApplication - documentName must match one of
 * the purpose's own CpfRuleDocument.documentName values exactly (CpfApplicationService.apply() validates
 * every mandatory one is present). s3Key/originalFilename come from a prior
 * POST /api/v1/documents/upload (category=CPF_WITHDRAWAL_SUPPORTING_DOC) call - this endpoint never accepts
 * a raw file itself, reusing the existing secure upload pipeline's own MIME/extension/size/magic-byte
 * validation rather than re-implementing it.
 */
public record CpfApplicationDocumentSubmission(
        @NotBlank String documentName,
        @NotBlank String s3Key,
        @NotBlank String originalFilename
) {
}
