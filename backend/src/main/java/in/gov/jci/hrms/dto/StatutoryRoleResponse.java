package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StatutoryRole;

import java.time.Instant;
import java.time.LocalDate;

public record StatutoryRoleResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String roleName,
        String appointmentOrderRef,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static StatutoryRoleResponse from(StatutoryRole role) {
        return new StatutoryRoleResponse(
                role.getId(),
                role.getEmployee().getId(),
                role.getEmployee().getEmployeeCode(),
                role.getRoleName(),
                role.getAppointmentOrderRef(),
                role.getStartDate(),
                role.getEndDate(),
                role.isActive(),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
