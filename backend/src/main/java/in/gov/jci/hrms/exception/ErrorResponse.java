package in.gov.jci.hrms.exception;

import java.time.Instant;
import java.util.List;

/**
 * fieldErrors is empty for every non-Bean-Validation failure (not-found,
 * conflict, business-rule errors) - those only ever have a single flat
 * `message`. Only handleValidation() (MethodArgumentNotValidException)
 * populates it, one entry per invalid @RequestBody field, so the frontend
 * can map a failed PUT/POST straight onto the offending form fields instead
 * of just showing the semicolon-joined message string.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, List.of());
    }

    public static ErrorResponse of(int status, String error, String message, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, fieldErrors);
    }
}
