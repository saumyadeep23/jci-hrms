package in.gov.jci.hrms.entity;

/** What corpus this incoming transfer actually carries - drives which of EmployeeIncomingFundTransfer's optional amount groups (CPF vs pension vs both) are expected to be populated. */
public enum IncomingTransferType {
    PF_ONLY,
    PENSION_ONLY,
    PF_AND_PENSION
}
