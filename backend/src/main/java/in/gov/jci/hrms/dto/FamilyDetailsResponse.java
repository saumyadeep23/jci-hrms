package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeFamilyDetails;

import java.time.LocalDate;

public record FamilyDetailsResponse(
        Long id,
        Long employeeId,
        String fatherName,
        String motherName,
        String spouseName,
        LocalDate spouseDob,
        int dependentCount
) {
    public static FamilyDetailsResponse from(EmployeeFamilyDetails details) {
        return new FamilyDetailsResponse(
                details.getId(),
                details.getEmployee().getId(),
                details.getFatherName(),
                details.getMotherName(),
                details.getSpouseName(),
                details.getSpouseDob(),
                details.getDependentCount()
        );
    }
}
