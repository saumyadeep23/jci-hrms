-- Pre-existing dev/UAT data can already violate the invariant this migration is about to enforce (rows
-- created before AttendanceRegularizationService.submit()'s duplicate-pending check existed). Auto-reject
-- every PENDING duplicate except the most recently created one per employee/attendance_date so the unique
-- index below can be created; this is a one-time cleanup of already-invalid state, not a business decision
-- an approver should have made, so it's flagged plainly in approver_remarks rather than silently discarded.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY employee_id, attendance_date ORDER BY created_at DESC, id DESC) AS rn
    FROM attendance_regularization_applications
    WHERE approval_status = 'PENDING'
)
UPDATE attendance_regularization_applications a
SET approval_status = 'REJECTED',
    approved_at = now(),
    approver_remarks = 'Auto-rejected by V73 migration: superseded by a later duplicate pending request for the same employee and attendance date.'
FROM ranked
WHERE a.id = ranked.id AND ranked.rn > 1;

-- Defense-in-depth alongside AttendanceRegularizationService.submit()'s own application-level check:
-- at most one PENDING regularization application per employee/attendance_date. A partial unique index
-- (not a plain unique constraint) so an employee can still have any number of APPROVED/REJECTED history
-- rows for the same date (e.g. a rejected request followed by a corrected resubmission).
CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_attendance_regularization
    ON attendance_regularization_applications (employee_id, attendance_date)
    WHERE approval_status = 'PENDING';
