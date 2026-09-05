package in.gov.jci.hrms.entity;

/** Physical lifecycle of the movement itself - separate from JoiningStatus, which tracks the joining report's own verification workflow. */
public enum MovementStatus {
    ORDERED,
    RELIEVED,
    JOINED
}
