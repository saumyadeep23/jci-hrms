package in.gov.jci.hrms.entity;

/**
 * employee_cea_claims.claim_status lifecycle: SUBMITTED -&gt; VERIFIED -&gt; BILL_PASSED -&gt; DISBURSED
 * (the last transition happens automatically the first time PayrollBatchComputationService.processBatch()
 * picks the claim up - see CeaClaimRepository.findPendingPayrollDisbursement()), or SUBMITTED/VERIFIED
 * -&gt; REJECTED. No DB CHECK constraint restricts this column (free VARCHAR(30)), so this enum is the
 * only thing constraining it on the write path this codebase controls.
 */
public enum CeaClaimStatus {
    SUBMITTED,
    VERIFIED,
    BILL_PASSED,
    DISBURSED,
    REJECTED
}
