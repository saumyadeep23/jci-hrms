package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CourseType;
import in.gov.jci.hrms.entity.DivisionClass;
import in.gov.jci.hrms.entity.EmployeeQualification;
import in.gov.jci.hrms.entity.QualificationLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record QualificationResponse(
        Long id,
        Long employeeId,
        QualificationLevel qualificationLevel,
        String degreeTitle,
        String specialization,
        String boardUniversity,
        String institutionName,
        Integer passingYear,
        BigDecimal percentageCgpa,
        DivisionClass divisionClass,
        CourseType courseType,
        boolean highestQualification,
        String certificateDocumentS3Key,
        boolean verified,
        String verifiedBy,
        Instant verifiedAt
) {
    public static QualificationResponse from(EmployeeQualification qualification) {
        return new QualificationResponse(
                qualification.getId(),
                qualification.getEmployee().getId(),
                qualification.getQualificationLevel(),
                qualification.getDegreeTitle(),
                qualification.getSpecialization(),
                qualification.getBoardUniversity(),
                qualification.getInstitutionName(),
                qualification.getPassingYear(),
                qualification.getPercentageCgpa(),
                qualification.getDivisionClass(),
                qualification.getCourseType(),
                qualification.isHighestQualification(),
                qualification.getCertificateDocumentS3Key(),
                qualification.isVerified(),
                qualification.getVerifiedBy(),
                qualification.getVerifiedAt()
        );
    }
}
