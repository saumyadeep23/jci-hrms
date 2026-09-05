package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.entity.PastServicePayScalePattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PastServiceRecordResponse(
        Long id,
        Long employeeId,
        String organizationName,
        PastServiceOrganizationType organizationType,
        String designationHeld,
        LocalDate fromDate,
        LocalDate toDate,
        Integer totalServiceDays,
        PastServicePayScalePattern lastPayScalePattern,
        BigDecimal lastDrawnBasic,
        BigDecimal lastDrawnGross,
        boolean qualifyingForPensionGratuity,
        String qualifyingServiceOrderRef,
        String reasonForLeaving,
        String experienceCertificateS3Key,
        String relievingNocDocumentS3Key,
        boolean verified,
        String verifiedBy,
        Instant verifiedAt
) {
    public static PastServiceRecordResponse from(EmployeePastServiceRecord record) {
        return new PastServiceRecordResponse(
                record.getId(),
                record.getEmployee().getId(),
                record.getOrganizationName(),
                record.getOrganizationType(),
                record.getDesignationHeld(),
                record.getFromDate(),
                record.getToDate(),
                record.getTotalServiceDays(),
                record.getLastPayScalePattern(),
                record.getLastDrawnBasic(),
                record.getLastDrawnGross(),
                record.isQualifyingForPensionGratuity(),
                record.getQualifyingServiceOrderRef(),
                record.getReasonForLeaving(),
                record.getExperienceCertificateS3Key(),
                record.getRelievingNocDocumentS3Key(),
                record.isVerified(),
                record.getVerifiedBy(),
                record.getVerifiedAt()
        );
    }
}
