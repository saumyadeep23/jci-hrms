package in.gov.jci.hrms.entity;

/**
 * The upload endpoint's own category vocabulary (POST /api/v1/documents/upload)
 * - broader than EmployeeDocument.documentCategory (which is constrained by
 * employee_documents' own CHECK constraint, V33 migration). BANK_PROOF/
 * QUALIFICATION/PAST_SERVICE_NOC never get written to employee_documents at
 * all - their resulting fileS3Key is instead threaded back into
 * employee_bank_accounts.cancelled_cheque_s3_key / employee_qualifications.
 * certificate_document_s3_key / employee_past_service_records.*_s3_key by
 * whichever onboarding step submits it. This enum only drives upload-time
 * validation (see UploadCategoryPolicy) and is never itself persisted.
 */
public enum UploadCategory {
    PHOTO,
    SIGNATURE,
    PAN_CARD,
    AADHAAR,
    CASTE_CERT,
    PWBD_CERT,
    BANK_PROOF,
    QUALIFICATION,
    PAST_SERVICE_NOC,
    APPOINTMENT_ORDER,
    JOINING_REPORT,
    SERVICE_BOOK_SCAN,
    APAR,
    DISCIPLINARY,
    /** CPF Transaction Dispute evidence (Passbook V2) - the resulting fileS3Key is threaded onto CpfTransactionDispute.attachmentS3Key by CpfTransactionDisputeService, the same "caller links it back" pattern as BANK_PROOF/QUALIFICATION. */
    CPF_DISPUTE_ATTACHMENT,
    /** CPF Trust withdrawal/loan application supporting document (Part 29) - the resulting fileS3Key/originalFileName is referenced by code (CpfApplicationDocumentSubmission) in the apply() request body, not written back onto any entity by this upload call itself. */
    CPF_WITHDRAWAL_SUPPORTING_DOC,
    OTHER
}
