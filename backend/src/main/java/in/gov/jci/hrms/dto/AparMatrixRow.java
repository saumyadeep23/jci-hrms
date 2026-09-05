package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/** One row of the APAR routing matrix - resolved from vw_apar_routing_matrix (PIMS_SPEC.md Feature 6). */
public record AparMatrixRow(
        Long appraiseeEmployeeId,
        String appraiseeEmployeeCode,
        String appraiseeName,
        String postTitle,
        String assignmentType,
        LocalDate assignmentStartDate,
        String reportingOfficerName,
        String reviewingOfficerName,
        String acceptingOfficerName,
        boolean dualChargeOver90Days
) {
}
