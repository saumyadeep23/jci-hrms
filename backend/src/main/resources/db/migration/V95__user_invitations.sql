-- Onboarding invitation lifecycle (docs/security/ONBOARDING_IMPLEMENTATION.md). Additive only.
-- token_hash is the ONLY token representation ever persisted - the raw token is never stored
-- anywhere (ONBOARDING_SECURITY_REQUIREMENTS.md).

CREATE TABLE user_invitations (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES application_users(id),
    initiated_by_user_id BIGINT NOT NULL REFERENCES application_users(id),
    token_hash          VARCHAR(128) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ,
    revoked_by_user_id  BIGINT REFERENCES application_users(id),
    revoked_at          TIMESTAMPTZ,
    version_no          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_invitations_user ON user_invitations (user_id);

-- A token hash must be unique among invitations that can still be consumed (status = INVITED) -
-- collision-astronomically-unlikely given SecureRandom + SHA-256, but a real DB backstop rather
-- than relying solely on the application's own check-before-insert.
CREATE UNIQUE INDEX uq_user_invitations_active_token ON user_invitations (token_hash) WHERE status = 'INVITED';
