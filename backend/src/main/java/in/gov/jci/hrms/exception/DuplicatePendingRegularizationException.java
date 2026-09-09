package in.gov.jci.hrms.exception;

/**
 * An employee already has a PENDING regularization application for the same attendance date - a
 * distinct 409 (Conflict) rather than the generic 400 every other BusinessRuleViolationException maps
 * to, since the request is well-formed and the conflict is with existing in-progress state, not a
 * validation failure. Same pattern as PredecessorPayrollUnfinalizedException.
 */
public class DuplicatePendingRegularizationException extends BusinessRuleViolationException {

    public DuplicatePendingRegularizationException(String message) {
        super(message);
    }
}
