package in.gov.jci.hrms.util;

import java.time.LocalDate;

/**
 * Java mirror of fn_calculate_jci_superannuation_date (V31/V53 migrations) - regular staff retire
 * at 58, Board Directors at 60 or 5 years from their director appointment date, whichever is
 * earlier; both age-based dates land on the last day of the birth month (or the preceding month, if
 * born on the 1st). The DB trigger remains the authoritative, persisted source of truth for actual
 * employee records (it also accounts for Ministry Extension, which this utility does not); this
 * class exists for callers that need the same rule without a round-trip - the live badge in
 * EditEmployeeModal.tsx's calculateSuperannuation() mirrors this logic in TypeScript, and
 * SuperannuationExtensionService uses this directly for its calculation-preview endpoint.
 */
public final class SuperannuationCalculator {

    private static final int REGULAR_RETIREMENT_AGE = 58;
    private static final int DIRECTOR_RETIREMENT_AGE = 60;
    private static final int DIRECTOR_TERM_YEARS = 5;

    private SuperannuationCalculator() {
    }

    /**
     * @param dob        date of birth
     * @param doj        the director's appointment date (post_incumbency.start_date in the DB
     *                   trigger) - ignored for non-directors; may be null for a director whose
     *                   appointment date isn't known yet, in which case only the age-60 date applies
     * @param isDirector whether this employee currently holds a Board Director-category post
     */
    public static LocalDate calculateSuperannuationDate(LocalDate dob, LocalDate doj, boolean isDirector) {
        LocalDate ageBasedDate = lastDayRuleAnniversary(dob, isDirector ? DIRECTOR_RETIREMENT_AGE : REGULAR_RETIREMENT_AGE);
        if (!isDirector || doj == null) {
            return ageBasedDate;
        }
        LocalDate termEndDate = doj.plusYears(DIRECTOR_TERM_YEARS).minusDays(1);
        return termEndDate.isBefore(ageBasedDate) ? termEndDate : ageBasedDate;
    }

    /** The last day of the birth month at the given age - or the last day of the preceding month, if born on the 1st. */
    public static LocalDate lastDayRuleAnniversary(LocalDate dob, int age) {
        LocalDate targetBirthday = dob.plusYears(age);
        if (dob.getDayOfMonth() == 1) {
            return targetBirthday.withDayOfMonth(1).minusDays(1);
        }
        return targetBirthday.withDayOfMonth(1).plusMonths(1).minusDays(1);
    }
}
