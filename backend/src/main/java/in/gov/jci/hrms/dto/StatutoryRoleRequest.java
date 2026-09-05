package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StatutoryRoleRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 50) String roleName,
        @Size(max = 100) String appointmentOrderRef,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Boolean active
) {
}
