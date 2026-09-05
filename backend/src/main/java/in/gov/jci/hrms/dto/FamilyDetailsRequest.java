package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** employeeId is a path variable on the owning controller - see EmployeeFamilyController. */
public record FamilyDetailsRequest(
        @NotBlank @Size(max = 150) String fatherName,
        @Size(max = 150) String motherName,
        @Size(max = 150) String spouseName,
        LocalDate spouseDob
) {
}
