package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record FunctionalRoleAssignmentRequest(
        @NotNull UUID roleId,
        @NotNull Long employeeId,
        Long departmentId,
        Long officeId,
        @Size(max = 50) String zoneCode,
        @NotBlank @Size(max = 100) String officeOrderRef,
        @NotNull LocalDate orderDate,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        boolean isPrimaryRole
) {
}
