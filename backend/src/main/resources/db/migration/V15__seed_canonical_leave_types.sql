-- Seeds the leave-type codes the attendance/leave engine's business logic will
-- hardcode string comparisons against in later phases (Commuted Leave draws from
-- HPL, half-day sessions are CL-only, etc.) - without this row existing with
-- exactly these codes, that logic has nothing to resolve and throws.
--
-- Quota/accumulation/encashable/career-limit figures below are CCS(Leave)Rules
-- 1972-style placeholders, not authoritative HR-approved figures - annual_quota is
-- a per-year credit for CL/EL/HPL/RH, but Maternity/Paternity/CCL are event- or
-- career-limited rather than annually credited, which this schema's per-year
-- LeaveBalance model doesn't cleanly represent; HR should review and correct all
-- of these via the new LeaveTypeController before this feature is relied upon for
-- real leave accounting.
INSERT INTO leave_types (code, name, annual_quota, max_accumulation_days, is_encashable, career_limit_days, is_active) VALUES
    ('CL', 'Casual Leave', 8.0, NULL, false, NULL, true),
    ('EL', 'Earned Leave', 30.0, 300, true, NULL, true),
    ('HPL', 'Half Pay Leave', 20.0, NULL, false, NULL, true),
    ('RH', 'Restricted Holiday', 2.0, NULL, false, NULL, true),
    -- Draws 2x days from HPL's own balance rather than being credited/debited
    -- independently (Phase D) - annual_quota is 0 on purpose, not a placeholder.
    ('COMMUTED', 'Commuted Leave', 0.0, NULL, false, NULL, true),
    ('MATERNITY', 'Maternity Leave', 180.0, NULL, false, NULL, true),
    ('PATERNITY', 'Paternity Leave', 15.0, NULL, false, NULL, true),
    ('CCL', 'Child Care Leave', 0.0, NULL, false, 730, true);
