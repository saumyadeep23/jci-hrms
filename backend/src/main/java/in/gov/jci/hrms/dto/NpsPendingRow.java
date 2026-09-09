package in.gov.jci.hrms.dto;

/** One row of the HR admin dashboard's "Pending / Not Submitted" tab - an active, NPS-eligible employee with no declaration for the selected FY. */
public record NpsPendingRow(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String officeOrDpc
) {
}
