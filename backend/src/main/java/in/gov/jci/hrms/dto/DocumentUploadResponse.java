package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.UploadCategory;

import java.time.Instant;

public record DocumentUploadResponse(
        String fileS3Key,
        UploadCategory documentCategory,
        String originalFileName,
        String mimeType,
        long fileSizeBytes,
        Instant uploadedAt
) {
}
