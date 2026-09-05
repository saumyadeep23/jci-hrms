package in.gov.jci.hrms.entity;

/** FORENOON/AFTERNOON - for release_session (user-entered) and joining_session (server-evaluated from the DB clock, never client-supplied - see JoiningReportService). */
public enum SessionType {
    FORENOON,
    AFTERNOON
}
