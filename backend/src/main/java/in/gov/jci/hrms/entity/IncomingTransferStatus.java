package in.gov.jci.hrms.entity;

/** employee_incoming_fund_transfers.status lifecycle - see IncomingFundTransferService. */
public enum IncomingTransferStatus {
    SUBMITTED,
    VERIFIED_BY_TRUST,
    CREDITED_TO_LEDGER,
    REJECTED
}
