package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.Pattern;

/** PUT /api/v1/payroll/trust/members/{employeeId}/uan - same 12-digit UAN pattern EmployeeRequest.uanNo already validates. */
public record UpdateUanRequest(
        @Pattern(regexp = "^[0-9]{12}$", message = "UAN must be exactly 12 digits") String uanNo
) {
}
