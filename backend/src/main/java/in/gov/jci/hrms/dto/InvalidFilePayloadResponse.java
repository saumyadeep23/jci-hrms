package in.gov.jci.hrms.dto;

import java.time.Instant;
import java.util.Set;

public record InvalidFilePayloadResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String field,
        Set<String> allowedMimeTypes,
        Long maxAllowedSizeBytes
) {
}
