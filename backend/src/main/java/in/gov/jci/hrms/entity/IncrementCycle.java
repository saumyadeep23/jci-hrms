package in.gov.jci.hrms.entity;

/**
 * The calendar month an employee's annual increment falls due (their
 * date-of-joining anniversary month) - one value per month, not just a
 * simplistic JULY/JANUARY IDA/CDA split. Widened from the original
 * JULY/JANUARY-only version to match the live dev DB's regular_pay_fixations
 * CHECK constraint, which already allowed all twelve months (see V61) -
 * that DB-side widening had drifted ahead of this enum undocumented, and any
 * fixation row outside JULY/JANUARY crashed every code path that fully
 * hydrates a RegularPayFixation entity (payroll compute, LPC certificate,
 * movement orders, increment processing, terminal settlement, and the
 * encashment admin review queue all fully hydrate this entity).
 */
public enum IncrementCycle {
    JANUARY,
    FEBRUARY,
    MARCH,
    APRIL,
    MAY,
    JUNE,
    JULY,
    AUGUST,
    SEPTEMBER,
    OCTOBER,
    NOVEMBER,
    DECEMBER
}
