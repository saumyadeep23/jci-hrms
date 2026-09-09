package in.gov.jci.hrms.entity;

/**
 * Matches employee_dependents.relationship and employee_nominees.relationship's DB CHECK constraint
 * exactly (see V71) - both columns were free-text VARCHAR(50) with no enum anywhere until this
 * refactor introduced one, driven by the Family & Nominees edit tab's need for a fixed dropdown and
 * CEA eligibility logic (only SON/DAUGHTER rows are ever CEA-eligible).
 */
public enum FamilyRelationshipType {
    FATHER,
    MOTHER,
    SPOUSE,
    SON,
    DAUGHTER
}
