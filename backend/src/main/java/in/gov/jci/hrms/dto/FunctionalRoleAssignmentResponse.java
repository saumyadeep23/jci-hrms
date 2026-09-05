package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeFunctionalRoleAssignment;
import in.gov.jci.hrms.entity.RoleCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FunctionalRoleAssignmentResponse(
        UUID id,
        UUID roleId,
        String roleCode,
        String roleName,
        RoleCategory roleCategory,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String designationTitle,
        Long departmentId,
        String departmentName,
        Long officeId,
        String officeName,
        String zoneCode,
        String officeOrderRef,
        LocalDate orderDate,
        LocalDate validFrom,
        LocalDate validTo,
        boolean isPrimaryRole,
        boolean active,
        Instant createdAt
) {
    public static FunctionalRoleAssignmentResponse from(EmployeeFunctionalRoleAssignment assignment) {
        return new FunctionalRoleAssignmentResponse(
                assignment.getId(),
                assignment.getRole().getId(),
                assignment.getRole().getRoleCode(),
                assignment.getRole().getRoleName(),
                assignment.getRole().getRoleCategory(),
                assignment.getEmployee().getId(),
                assignment.getEmployee().getEmployeeCode(),
                assignment.getEmployee().getFullName(),
                assignment.getEmployee().getDesignation().getTitle(),
                assignment.getDepartment() != null ? assignment.getDepartment().getId() : null,
                assignment.getDepartment() != null ? assignment.getDepartment().getName() : null,
                assignment.getOffice() != null ? assignment.getOffice().getId() : null,
                assignment.getOffice() != null ? assignment.getOffice().getName() : null,
                assignment.getZoneCode(),
                assignment.getOfficeOrderRef(),
                assignment.getOrderDate(),
                assignment.getValidFrom(),
                assignment.getValidTo(),
                assignment.isPrimaryRole(),
                assignment.isActive(),
                assignment.getCreatedAt()
        );
    }
}
