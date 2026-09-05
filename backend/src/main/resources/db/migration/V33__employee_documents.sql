-- PIMS_SPEC.md Step 8 (Document Verification & Final Review).
CREATE TABLE IF NOT EXISTS employee_documents (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    document_category   VARCHAR(50)  NOT NULL,
    document_title       VARCHAR(200) NOT NULL,
    file_s3_key           VARCHAR(500) NOT NULL,
    mime_type              VARCHAR(50)  NOT NULL DEFAULT 'application/pdf',
    file_size_bytes         BIGINT,
    is_verified              BOOLEAN      NOT NULL DEFAULT false,
    verified_by              VARCHAR(150),
    verified_at              TIMESTAMPTZ,
    uploaded_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT employee_documents_document_category_check
        CHECK (document_category IN ('PHOTO', 'SIGNATURE', 'PAN_CARD', 'AADHAAR', 'CASTE_CERT', 'PWBD_CERT',
                                      'APPOINTMENT_ORDER', 'JOINING_REPORT', 'SERVICE_BOOK_SCAN', 'APAR', 'DISCIPLINARY', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_employee_docs ON employee_documents (employee_id, document_category);
