package in.gov.jci.hrms.entity;

/**
 * Matches regular_pay_fixations.fixation_reason's DB CHECK constraint exactly (see V62) - the two
 * prior Java-only names this enum used to carry, INITIAL_APPOINTMENT and CORRECTION, never had a
 * matching DB-accepted value and were dropped rather than kept as extra constants: neither is
 * exposed on any request DTO (fixationReason is always server-set, never deserialized from a
 * caller), so there was no compatibility surface an alias would actually serve, and keeping either
 * around as a "valid" enum constant would just recreate the exact class of bug this migration
 * fixes - a Java value the DB rejects, crashing the first time anything actually persists it.
 * INITIAL_APPOINTMENT's real replacement is INITIAL_FIXATION (same "employee's very first fixation"
 * concept, DB's own name for it) - see EmployeeOnboardingService, which used to set the old name and
 * would have failed its INSERT with a constraint violation the moment onboarding.
 */
public enum FixationReason {
    INITIAL_FIXATION,
    ONBOARDING,
    PROMOTION,
    ANNUAL_INCREMENT,
    FINANCIAL_UPGRADATION,
    PAY_REVISION,
    DEMOTION,
    MIGRATION
}
