-- Multi-tier leave routing on top of leave_applications' existing single-tier status/approver
-- columns (status stays the authoritative APPROVED/REJECTED/etc for balance-reservation logic -
-- see LeaveApplicationService; workflow_stage is the new, separately-tracked routing stage that can
-- pass through zero or more RECOMMENDED forwards before reaching SANCTIONED/REJECTED).
-- current_assigned_to is "whose desk the file is on right now" - set to the originally-resolved
-- approver_employee_id at submit() time, then reassigned on every forward().
ALTER TABLE leave_applications
    ADD COLUMN current_assigned_to BIGINT REFERENCES employees (id),
    ADD COLUMN workflow_stage       VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    ADD CONSTRAINT ck_leave_applications_workflow_stage
        CHECK (workflow_stage IN ('SUBMITTED', 'RECOMMENDED', 'SANCTIONED', 'REJECTED', 'CANCELLED'));

CREATE INDEX ix_leave_applications_current_assigned_to ON leave_applications (current_assigned_to);

-- Full audit trail of every routing/decision step, independent of leave_applications' own
-- current-state columns above - a forward doesn't overwrite history, it appends to it.
CREATE TABLE leave_application_actions (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES leave_applications (id),
    action_by      BIGINT      NOT NULL REFERENCES employees (id),
    action_type    VARCHAR(30) NOT NULL,
    forwarded_to   BIGINT REFERENCES employees (id),
    remarks        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_leave_application_actions_action_type
        CHECK (action_type IN ('SUBMIT', 'RECOMMEND_FORWARD', 'SANCTION', 'REJECT'))
);

CREATE INDEX ix_leave_application_actions_application_id ON leave_application_actions (application_id);
