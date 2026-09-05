package in.gov.jci.hrms.exception;

/** A create/update collided with an existing record on a business-unique field (e.g. GSTIN) rather than the primary key. Maps to HTTP 409 via GlobalExceptionHandler's MasterDataConflictException handler. */
public class DuplicateResourceException extends MasterDataConflictException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
