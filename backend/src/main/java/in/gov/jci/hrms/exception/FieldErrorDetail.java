package in.gov.jci.hrms.exception;

/** One field-level validation failure, surfaced alongside ErrorResponse.message's flat summary so the frontend can map errors onto individual form fields. */
public record FieldErrorDetail(String field, String message) {
}
