package in.gov.jci.hrms.exception;

import org.springframework.http.HttpStatus;

import java.util.Set;

/** POST /api/v1/documents/upload validation failures - see DocumentUploadController's dedicated error shape. */
public class InvalidFilePayloadException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String field;
    private final Set<String> allowedMimeTypes;
    private final Long maxAllowedSizeBytes;

    public InvalidFilePayloadException(HttpStatus status, String errorCode, String message, String field,
                                        Set<String> allowedMimeTypes, Long maxAllowedSizeBytes) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.field = field;
        this.allowedMimeTypes = allowedMimeTypes;
        this.maxAllowedSizeBytes = maxAllowedSizeBytes;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }

    public Set<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public Long getMaxAllowedSizeBytes() {
        return maxAllowedSizeBytes;
    }
}
