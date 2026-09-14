package in.gov.jci.hrms.entity;

/** jcieccs_loan_schedule.status - DB-enforced via chk_jcieccs_sched_status. */
public enum JciEccsScheduleStatus {
    FUTURE,
    DUE,
    PARTIAL,
    PAID,
    OVERDUE,
    RESTRUCTURED,
    WAIVED
}
