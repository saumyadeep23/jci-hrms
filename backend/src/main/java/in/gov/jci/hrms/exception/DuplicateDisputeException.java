package in.gov.jci.hrms.exception;

/** An active (not RESOLVED/REJECTED/WITHDRAWN) dispute already exists for this (transaction, category) pair - Part 10/31 of the CPF Passbook V2 module spec. 409 rather than the generic 400, same pattern as PredecessorPayrollUnfinalizedException. */
public class DuplicateDisputeException extends BusinessRuleViolationException {

    public DuplicateDisputeException(String message) {
        super(message);
    }
}
