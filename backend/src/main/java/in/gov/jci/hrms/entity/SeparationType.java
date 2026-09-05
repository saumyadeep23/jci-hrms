package in.gov.jci.hrms.entity;

/** Shared by exit_clearance_requests and terminal_settlements (V58) - kept as one enum so the two stay in lockstep. */
public enum SeparationType {
    SUPERANNUATION,
    RESIGNATION,
    VRS,
    DECEASED,
    TERMINATED
}
