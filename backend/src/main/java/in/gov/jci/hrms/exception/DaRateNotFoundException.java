package in.gov.jci.hrms.exception;

import in.gov.jci.hrms.entity.ScaleType;

import java.time.LocalDate;

/** No active DA rate row's effective range covers the requested (scaleType, date) pair. */
public class DaRateNotFoundException extends RuntimeException {

    public DaRateNotFoundException(ScaleType scaleType, LocalDate effectiveDate) {
        super("No active DA rate found for " + scaleType + " as of " + effectiveDate);
    }
}
