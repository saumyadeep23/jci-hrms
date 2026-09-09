-- Dev/test-only cleanup for rows that predate the once-per-calendar-year IN_SERVICE_EL encashment
-- rule added to LeaveEncashmentService.apply() (see the "once-per-calendar-year statutory
-- encashment rule" task). NOT a Flyway migration - it lives outside db/migration/ deliberately, so
-- it is never auto-applied to any environment; run it by hand against a dev/test database only:
--
--   docker exec -i jci-hrms-local-db psql -U jcihrms_admin -d jcihrms \
--       < src/main/resources/db/dev-scripts/cleanup-duplicate-in-service-el-encashments.sql
--
-- For every employee/year with more than one not-rejected IN_SERVICE_EL application, this keeps
-- the earliest (by created_at) and fully reverses every later one's side effects - entitlement
-- balance debit, EL_ENCASHMENT_DEBIT ledger entry, and the sanction's e-Service Book entry - before
-- deleting the application row itself, so the dev DB ends up exactly as if the duplicate claim had
-- never been submitted.
--
-- Safety guard: an application already queued into or paid out through a payroll run
-- (payroll_run_id / arrear_payroll_run_id set, or is_payroll_processed = true) is left untouched
-- and reported instead of being silently deleted, since that would mean reversing an actual
-- payroll disbursement rather than cleaning up test data.

DO $$
DECLARE
    dup RECORD;
    skipped_ids BIGINT[] := ARRAY[]::BIGINT[];
BEGIN
    FOR dup IN
        SELECT a.id, a.employee_id, a.el_days_claimed, a.service_book_entry_id,
               a.is_payroll_processed, a.payroll_run_id, a.arrear_payroll_run_id
        FROM leave_encashment_application a
        WHERE a.encashment_type = 'IN_SERVICE_EL'
          AND a.hr_approval_status <> 'REJECTED'
          AND a.finance_approval_status <> 'REJECTED'
          AND a.id NOT IN (
              SELECT DISTINCT ON (employee_id, EXTRACT(YEAR FROM created_at)) id
              FROM leave_encashment_application
              WHERE encashment_type = 'IN_SERVICE_EL'
                AND hr_approval_status <> 'REJECTED'
                AND finance_approval_status <> 'REJECTED'
              ORDER BY employee_id, EXTRACT(YEAR FROM created_at), created_at ASC
          )
    LOOP
        IF dup.is_payroll_processed OR dup.payroll_run_id IS NOT NULL OR dup.arrear_payroll_run_id IS NOT NULL THEN
            skipped_ids := array_append(skipped_ids, dup.id);
            CONTINUE;
        END IF;

        -- Reverse finalizeEncashment()'s debit - only ran for a duplicate Finance actually approved;
        -- a still-pending/HR-only-approved duplicate never touched these columns, only the
        -- reservation columns reversed below.
        UPDATE leave_entitlement_balance b
        SET encashable_current = encashable_current + dup.el_days_claimed,
            encashable_encashed = GREATEST(encashable_encashed - dup.el_days_claimed, 0),
            encashed_days = GREATEST(encashed_days - dup.el_days_claimed, 0),
            current_balance = current_balance + dup.el_days_claimed,
            available_balance = available_balance + dup.el_days_claimed
        FROM leave_types lt
        WHERE b.leave_type_id = lt.id AND lt.code = 'EL'
          AND b.employee_id = dup.employee_id
          AND b.year = EXTRACT(YEAR FROM (SELECT created_at FROM leave_encashment_application WHERE id = dup.id))
          AND EXISTS (SELECT 1 FROM leave_encashment_application WHERE id = dup.id AND finance_approval_status = 'APPROVED');

        -- Release any still-outstanding reservation (a duplicate that never reached Finance approval).
        UPDATE leave_entitlement_balance b
        SET encashable_reserved = GREATEST(encashable_reserved - dup.el_days_claimed, 0),
            encashable_available = encashable_available + dup.el_days_claimed,
            available_balance = available_balance + dup.el_days_claimed
        FROM leave_types lt
        WHERE b.leave_type_id = lt.id AND lt.code = 'EL'
          AND b.employee_id = dup.employee_id
          AND b.year = EXTRACT(YEAR FROM (SELECT created_at FROM leave_encashment_application WHERE id = dup.id))
          AND EXISTS (SELECT 1 FROM leave_encashment_application WHERE id = dup.id AND finance_approval_status <> 'APPROVED');

        DELETE FROM leave_ledger_entries
        WHERE source = 'EL_ENCASHMENT_DEBIT' AND description = 'EL encashment debit (IN_SERVICE_EL) - application ' || dup.id;

        -- The application row references the service-book entry (not the reverse), so it must be
        -- cleared/deleted before the service-book row it points to can be removed.
        UPDATE leave_encashment_application SET service_book_entry_id = NULL WHERE id = dup.id;
        DELETE FROM leave_encashment_application WHERE id = dup.id;

        IF dup.service_book_entry_id IS NOT NULL THEN
            DELETE FROM employee_service_book WHERE id = dup.service_book_entry_id;
        END IF;

        RAISE NOTICE 'Removed duplicate IN_SERVICE_EL encashment application % for employee %', dup.id, dup.employee_id;
    END LOOP;

    IF array_length(skipped_ids, 1) > 0 THEN
        RAISE NOTICE 'Skipped % duplicate application(s) already queued into payroll - resolve manually: %',
            array_length(skipped_ids, 1), skipped_ids;
    END IF;
END $$;
