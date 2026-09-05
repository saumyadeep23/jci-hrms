package in.gov.jci.hrms.exception;

/**
 * A combined CL+RH application whose CL leg is contiguous with an existing
 * APPROVED EL/HPL/CCL application - strictly rejected (PIMS ALMS Phase 2,
 * Section 3), as 422 rather than the generic 400 every other
 * BusinessRuleViolationException maps to, mirroring
 * InsufficientLeaveBalanceException's pattern: the payload is otherwise
 * well-formed, it's specifically the combination that's unprocessable.
 */
public class IncompatibleLeaveCombinationException extends BusinessRuleViolationException {

    public IncompatibleLeaveCombinationException(String message) {
        super(message);
    }
}
