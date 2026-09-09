package in.gov.jci.hrms.entity;

/** da_projection_batches.status - free VARCHAR(20), no DB CHECK constraint, so this enum is the only thing constraining it on the write path. */
public enum DaProjectionBatchStatus {
    DRAFT,
    ORDER_COMMITTED
}
