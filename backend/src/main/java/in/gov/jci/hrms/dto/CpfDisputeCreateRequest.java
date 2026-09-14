package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfDisputeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/ess/cpf/disputes - CpfTransactionDisputeService.raiseDispute(). attachmentS3Key/
 * attachmentOriginalFilename come from a prior POST /api/v1/documents/upload (category=CPF_DISPUTE_ATTACHMENT)
 * call - this endpoint never accepts a raw file itself, reusing the existing secure upload pipeline's own
 * MIME/extension/size/magic-byte validation rather than re-implementing it (Part 27).
 */
public record CpfDisputeCreateRequest(
        @NotNull Long cpfLedgerTransactionId,
        @NotNull CpfDisputeCategory disputeCategory,
        @NotBlank @Size(max = 4000) String employeeRemarks,
        String attachmentS3Key,
        String attachmentOriginalFilename
) {
}
