package in.gov.jci.hrms.entity;

/** employee_deputation_records.deputation_direction - free VARCHAR(20), no DB CHECK constraint. DEPUTATION_OUT = a JCI employee sent to another organization (still on JCI's own payroll if drawing parent pay - see PayrollBatchComputationService's Head 67 hook); DEPUTATION_IN = an external officer borrowed into JCI. */
public enum DeputationDirection {
    DEPUTATION_OUT,
    DEPUTATION_IN
}
