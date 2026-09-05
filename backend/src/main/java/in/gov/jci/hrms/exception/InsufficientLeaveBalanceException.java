package in.gov.jci.hrms.exception;

/**
 * A leave-application submission that would debit more days than the
 * employee's available balance for that leave type/year - a distinct 422
 * (Unprocessable Entity) rather than the generic 400 every other
 * BusinessRuleViolationException maps to, since the payload is otherwise
 * well-formed and every other business rule passed; it's the balance figure
 * specifically that makes it unprocessable. A subtype (not a sibling) of
 * BusinessRuleViolationException so existing `catch
 * (BusinessRuleViolationException)` call sites - e.g.
 * LeaveApplicationService.preview() - keep catching it unchanged.
 */
public class InsufficientLeaveBalanceException extends BusinessRuleViolationException {

    public InsufficientLeaveBalanceException(String message) {
        super(message);
    }
}
