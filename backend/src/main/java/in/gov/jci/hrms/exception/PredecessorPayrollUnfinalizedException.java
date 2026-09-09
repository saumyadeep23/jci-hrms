package in.gov.jci.hrms.exception;

/**
 * A retro month required by an IDA arrear drawal has no DISBURSED payroll_batches row yet - a distinct
 * 409 (Conflict) rather than the generic 400 every other BusinessRuleViolationException maps to, since
 * the request is well-formed and will very likely succeed once that month's payroll is actually
 * finalized (a timing conflict with in-progress state, not a validation failure). A subtype (not a
 * sibling) of BusinessRuleViolationException, same pattern as InsufficientLeaveBalanceException.
 */
public class PredecessorPayrollUnfinalizedException extends BusinessRuleViolationException {

    public PredecessorPayrollUnfinalizedException(String message) {
        super(message);
    }
}
