package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Department;

import java.time.Instant;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        /** Always null for any row this response can actually carry - Department.@SQLRestriction("deleted_at IS NULL") means a soft-deleted row never reaches here in the first place. Exposed anyway so the frontend's row.deletedAt === null "Active" check has real data to read instead of an always-undefined field. */
        Instant deletedAt
) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getDescription(),
                department.getCreatedAt(),
                department.getUpdatedAt(),
                department.getDeletedAt()
        );
    }
}
