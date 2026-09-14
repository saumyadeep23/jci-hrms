package in.gov.jci.hrms.entity;

/** jcieccs_recovery_allocation.component - DB-enforced via chk_jcieccs_recovery_alloc_component. Mirrors
 * the five JCIECCS payroll-demand components (heads 47/52/53/54/55) already established by
 * PayrollBatchComputationService/JciEccsCollectionDetail - not a new taxonomy. */
public enum JciEccsRecoveryComponent {
    THRIFT,
    TERM_INTEREST,
    TERM_PRINCIPAL,
    EMERGENCY_INTEREST,
    EMERGENCY_PRINCIPAL
}
