package in.gov.jci.hrms.entity;

/** cpf_loan_settlement_transactions.settlement_type - how a direct out-of-payroll settlement was received. Mirrors IncomingTransferPaymentMode's own value set, plus CASH since this table backs the "settle-cash" workflow specifically. */
public enum CpfLoanSettlementMode {
    CASH,
    CHEQUE,
    DEMAND_DRAFT,
    NEFT,
    RTGS,
    OTHER
}
