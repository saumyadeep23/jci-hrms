package in.gov.jci.hrms.entity;

public enum JoiningStatus {
    NOT_SUBMITTED,
    PENDING_VERIFICATION,
    /** Reviewing officer sent the joining report back for amendment - see JoiningReportService.requestClarification()/resubmitJoiningReport(). */
    CLARIFICATION_REQUESTED,
    ACCEPTED,
    REJECTED
}
