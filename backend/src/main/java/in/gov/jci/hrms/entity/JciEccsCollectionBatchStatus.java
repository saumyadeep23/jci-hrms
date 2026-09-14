package in.gov.jci.hrms.entity;

/** jcieccs_collection_batch.batch_status - DB-enforced via chk_jcieccs_batch_status. REOPENED covers
 * spec section 1.5's "unless Payroll explicitly cancels/reopens the batch". */
public enum JciEccsCollectionBatchStatus {
    LOCKED,
    PROCESSED,
    REOPENED
}
