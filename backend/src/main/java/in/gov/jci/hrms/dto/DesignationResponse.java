package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Designation;

import java.time.Instant;

public record DesignationResponse(
        Long id,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Long gradeScaleId,
        String scaleCode,
        Cadre cadre,
        Integer hierarchyLevel,
        Boolean boardLevel,
        String idaPayScale,
        /** Always null for any row this response can actually carry - Designation.@SQLRestriction("deleted_at IS NULL") means a soft-deleted row never reaches here in the first place. Exposed anyway so the frontend's row.deletedAt === null "Active" check has real data to read instead of an always-undefined field. */
        Instant deletedAt,
        /** "Director" drives fn_calculate_jci_superannuation_date's 60-year/5-year-tenure rule (V31/V53) - lets the frontend mirror that rule live while editing an employee, without waiting on a server round-trip. */
        String categoryType
) {
    public static DesignationResponse from(Designation designation) {
        var gradeScale = designation.getGradeScale();
        return new DesignationResponse(
                designation.getId(),
                designation.getTitle(),
                designation.getDescription(),
                designation.getCreatedAt(),
                designation.getUpdatedAt(),
                gradeScale != null ? gradeScale.getId() : null,
                gradeScale != null ? gradeScale.getScaleCode() : null,
                gradeScale != null ? gradeScale.getCadre() : null,
                gradeScale != null ? gradeScale.getHierarchyLevel() : null,
                gradeScale != null ? gradeScale.isBoardLevel() : null,
                gradeScale != null ? gradeScale.idaScaleLabel() : null,
                designation.getDeletedAt(),
                designation.getCategoryType()
        );
    }
}
