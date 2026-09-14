-- The rule engine already models which documents a purpose REQUIRES (cpf_rule_document, 11 seeded rows -
-- mandatory/optional per purpose, allowed MIME types, max size), but there was no column on cpf_application
-- itself to record what an applicant actually SUBMITTED against those requirements. Additive only - no
-- existing column renamed/dropped, no seeded rule data touched.
ALTER TABLE cpf_application
    ADD COLUMN IF NOT EXISTS submitted_documents JSONB NOT NULL DEFAULT '[]'::jsonb;
