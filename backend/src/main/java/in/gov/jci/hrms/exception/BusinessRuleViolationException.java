package in.gov.jci.hrms.exception;

/**
 * Generic 400 for business-rule violations outside the master-data domain
 * (e.g. insufficient leave balance, an invalid leave-application status
 * transition) - kept distinct from MasterDataValidationException, which is
 * scoped to Department/Designation/RO/DPC/PayScale/Post.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
