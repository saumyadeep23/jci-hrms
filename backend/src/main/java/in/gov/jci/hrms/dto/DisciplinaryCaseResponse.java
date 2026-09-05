package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DisciplinaryCase;
import in.gov.jci.hrms.entity.DisciplinaryCaseStatus;
import in.gov.jci.hrms.entity.DisciplinaryCaseType;
import in.gov.jci.hrms.entity.PenaltyType;

import java.time.Instant;
import java.time.LocalDate;

public record DisciplinaryCaseResponse(
        Long id,
        String caseNumber,
        Long employeeId,
        String employeeCode,
        DisciplinaryCaseType caseType,
        DisciplinaryCaseStatus status,
        LocalDate chargeSheetDate,
        Long inquiryOfficerId,
        String inquiryOfficerCode,
        PenaltyType penaltyType,
        LocalDate penaltyEffectiveFrom,
        LocalDate penaltyEffectiveTo,
        String remarks,
        Instant createdAt,
        Instant updatedAt
) {
    public static DisciplinaryCaseResponse from(DisciplinaryCase disciplinaryCase) {
        return new DisciplinaryCaseResponse(
                disciplinaryCase.getId(),
                disciplinaryCase.getCaseNumber(),
                disciplinaryCase.getEmployee().getId(),
                disciplinaryCase.getEmployee().getEmployeeCode(),
                disciplinaryCase.getCaseType(),
                disciplinaryCase.getStatus(),
                disciplinaryCase.getChargeSheetDate(),
                disciplinaryCase.getInquiryOfficer() != null ? disciplinaryCase.getInquiryOfficer().getId() : null,
                disciplinaryCase.getInquiryOfficer() != null ? disciplinaryCase.getInquiryOfficer().getEmployeeCode() : null,
                disciplinaryCase.getPenaltyType(),
                disciplinaryCase.getPenaltyEffectiveFrom(),
                disciplinaryCase.getPenaltyEffectiveTo(),
                disciplinaryCase.getRemarks(),
                disciplinaryCase.getCreatedAt(),
                disciplinaryCase.getUpdatedAt()
        );
    }
}
