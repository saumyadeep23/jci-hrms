package in.gov.jci.hrms.entity;

/** The nodal departments an exit clearance request is provisioned against, one item each - see
 * ExitClearanceService.initiateExit(). JCIECCS (Task 4 Phase 3) is the 8th - the co-operative credit
 * society's own no-dues checkpoint, reusing this exact PENDING/CLEARED/REJECTED_WITH_DUES workflow
 * rather than a second settlement status engine; see JciEccsNoDuesService. */
public enum ExitClearanceDepartment {
    ESTABLISHMENT,
    VIGILANCE,
    ESTATE,
    IT,
    FINANCE,
    STORES,
    CPF_TRUST,
    JCIECCS
}
