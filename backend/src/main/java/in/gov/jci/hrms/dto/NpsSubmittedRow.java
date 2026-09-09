package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the HR admin dashboard's "Submitted Declarations" tab. monthlyDeduction is computed against the employee's *current* Basic+DA, not historized against the pay in effect at submission time - null if the employee currently has no active pay fixation to compute it from. */
public record NpsSubmittedRow(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String officeOrDpc,
        BigDecimal declaredPercentage,
        BigDecimal monthlyDeduction,
        Instant submissionDate,
        String remarks
) {
}
