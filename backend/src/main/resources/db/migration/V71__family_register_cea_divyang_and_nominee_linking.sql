-- Family & Nominees edit-tab refactor: unifies employee_dependents into a "Family Register" that
-- can also drive CEA (Children Education Allowance) eligibility and links employee_nominees to a
-- specific dependent row instead of re-typing name/relationship for every PF/Gratuity nomination.
--
-- is_divyang, disability_percentage, and is_multiple_birth_second_delivery already exist on
-- employee_dependents in the shared dev database (created directly by other tooling before this
-- migration existed - same situation as V66/67/68/69's own comments), so those three are additive
-- IF NOT EXISTS to stay a no-op there and a real create on a fresh environment; gender and the
-- dependent_id link are genuinely new everywhere.

ALTER TABLE employee_dependents ADD COLUMN IF NOT EXISTS gender VARCHAR(30);
ALTER TABLE employee_dependents ADD COLUMN IF NOT EXISTS is_divyang BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE employee_dependents ADD COLUMN IF NOT EXISTS disability_percentage NUMERIC(5,2);
ALTER TABLE employee_dependents ADD COLUMN IF NOT EXISTS is_multiple_birth_second_delivery BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE employee_dependents DROP CONSTRAINT IF EXISTS ck_employee_dependents_disability_percentage;
ALTER TABLE employee_dependents ADD CONSTRAINT ck_employee_dependents_disability_percentage
    CHECK (disability_percentage IS NULL OR (disability_percentage >= 0 AND disability_percentage <= 100));

ALTER TABLE employee_dependents DROP CONSTRAINT IF EXISTS ck_employee_dependents_gender;
ALTER TABLE employee_dependents ADD CONSTRAINT ck_employee_dependents_gender
    CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'));

-- relationship on both tables was free-text end-to-end (entity/DTO/UI) with no enum anywhere;
-- existing real data is already clean title-case (Father/Mother/Son/Spouse), so normalizing to the
-- new FamilyRelationshipType enum's UPPER_SNAKE_CASE convention before constraining is a safe,
-- one-time, no-data-loss fixup rather than a risky migration.
UPDATE employee_dependents SET relationship = UPPER(relationship) WHERE relationship <> UPPER(relationship);
ALTER TABLE employee_dependents DROP CONSTRAINT IF EXISTS ck_employee_dependents_relationship;
ALTER TABLE employee_dependents ADD CONSTRAINT ck_employee_dependents_relationship
    CHECK (relationship IN ('FATHER', 'MOTHER', 'SPOUSE', 'SON', 'DAUGHTER'));

UPDATE employee_nominees SET relationship = UPPER(relationship) WHERE relationship <> UPPER(relationship);
ALTER TABLE employee_nominees DROP CONSTRAINT IF EXISTS ck_employee_nominees_relationship;
ALTER TABLE employee_nominees ADD CONSTRAINT ck_employee_nominees_relationship
    CHECK (relationship IN ('FATHER', 'MOTHER', 'SPOUSE', 'SON', 'DAUGHTER'));

-- nominee_for was likewise free text (UI placeholder suggested "PF, Gratuity, NPS" but nothing
-- enforced it, and no NPS-type rows exist in the shared dev database today) - the redesigned
-- Nomination Master only has PF and Gratuity tabs, so this constrains to exactly those two.
UPDATE employee_nominees SET nominee_for = UPPER(nominee_for) WHERE nominee_for <> UPPER(nominee_for);
ALTER TABLE employee_nominees DROP CONSTRAINT IF EXISTS ck_employee_nominees_nominee_for;
ALTER TABLE employee_nominees ADD CONSTRAINT ck_employee_nominees_nominee_for
    CHECK (nominee_for IN ('PF', 'GRATUITY'));

-- Links a nominee to the Family Register row it was selected from, eliminating re-entry of
-- name/relationship - EmployeeNominee.name/relationship are still stored (server-derived from this
-- link at save time, not client-typed) rather than dropped outright, so a later edit/removal of the
-- dependent doesn't silently corrupt a historical nomination record. Nullable: nominees created
-- through the pre-existing standalone /api/employees/{id}/nominees endpoint (kept for backward
-- compatibility) may still supply a nominee unlinked to any Family Register row.
ALTER TABLE employee_nominees ADD COLUMN IF NOT EXISTS dependent_id BIGINT REFERENCES employee_dependents(id);
CREATE INDEX IF NOT EXISTS ix_employee_nominees_dependent_id ON employee_nominees (dependent_id);
