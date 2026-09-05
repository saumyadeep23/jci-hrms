package in.gov.jci.hrms.dto;

/** Count of retirements falling within a given forecast window (6/12/24/36/60 months) - PIMS_SPEC.md's KPI ribbon. */
public record SuperannuationWindowSummary(int months, long count) {
}
