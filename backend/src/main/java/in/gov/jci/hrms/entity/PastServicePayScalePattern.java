package in.gov.jci.hrms.entity;

/** Distinct from ScaleType (current PayScale master data, IDA/CDA only) - a past employer's pay could also have been CONSOLIDATED or something else entirely. */
public enum PastServicePayScalePattern {
    IDA,
    CDA,
    CONSOLIDATED,
    OTHER
}
