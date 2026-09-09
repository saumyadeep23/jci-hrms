package in.gov.jci.hrms.entity;

/** leave_encashment_application.application_type - free VARCHAR(20), no DB CHECK constraint, so this enum is the only thing constraining it on the write path. DA_ARREAR rows are system-generated top-ups on an already HR+Finance-approved REGULAR encashment, created by IdaArrearComputationService when a new DA order is committed. */
public enum EncashmentApplicationType {
    REGULAR,
    DA_ARREAR
}
