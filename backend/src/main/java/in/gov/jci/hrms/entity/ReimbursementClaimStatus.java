package in.gov.jci.hrms.entity;

/**
 * Shared by MedicalClaim and TadaClaim - both follow the same 4-eyes
 * lifecycle (Draft -> Submit -> HR Verify -> Finance Approve, or Reject).
 */
public enum ReimbursementClaimStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED_BY_HR,
    APPROVED_BY_FINANCE,
    REJECTED
}
