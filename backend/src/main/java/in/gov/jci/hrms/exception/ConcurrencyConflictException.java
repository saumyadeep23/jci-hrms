package in.gov.jci.hrms.exception;

/** Optimistic-locking conflict (Part 29 of the CPF Passbook V2 module spec) - a caller's expectedVersion no longer matches the entity's current version (another user already changed it). 409, same pattern as PredecessorPayrollUnfinalizedException. */
public class ConcurrencyConflictException extends BusinessRuleViolationException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
