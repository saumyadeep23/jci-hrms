package in.gov.jci.hrms.entity;

/**
 * Matches employee_nominees.nominee_for's DB CHECK constraint exactly (see V71) - the redesigned
 * Nomination Master splits into exactly these two tabs (Provident Fund and Gratuity); no NPS-type
 * nominee rows exist in any environment this was checked against.
 */
public enum NominationType {
    PF,
    GRATUITY
}
