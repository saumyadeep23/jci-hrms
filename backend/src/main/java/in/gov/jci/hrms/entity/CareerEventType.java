package in.gov.jci.hrms.entity;

/**
 * Closed vocabulary for career events created via the new
 * POST /api/v1/employees/:id/service-book endpoint. employee_service_book.event_type
 * itself stays a free-text VARCHAR (legacy-migrated entries use arbitrary
 * labels - see EmployeeServiceBook's own javadoc), so this enum only
 * constrains new entries created through this endpoint, not the column itself.
 */
public enum CareerEventType {
    PROMOTION,
    TRANSFER,
    MACP,
    PAY_REVISION,
    PENALTY_WITHHOLD_INCREMENT,
    PENALTY_CENSUROUS,
    EOL_LWP,
    EARNED_LEAVE_ENCASHABLE,
    /** Movement lifecycle (JoiningReportService): relieved from the source station. */
    TRANSFER_RELEASE,
    /** Movement lifecycle: accepted joining at the destination station. */
    TRANSFER_JOINING,
    /** Movement lifecycle: unavailed Joining Time converted to EL credit on an administrative transfer. */
    TRANSFER_BENEFIT_EL_CREDIT,
    /** Movement lifecycle: promotional/transfer pay fixation applied for payroll verification. */
    PAY_FIXATION
}
