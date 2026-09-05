package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeApar;
import in.gov.jci.hrms.entity.EmployeeAparStatus;
import in.gov.jci.hrms.entity.FinalGrading;

import java.math.BigDecimal;
import java.time.Instant;

public record EmployeeAparResponse(
        Long id,
        Long aparCycleId,
        String cycleYear,
        Long employeeId,
        String employeeCode,
        Long reportingOfficerId,
        Long reviewingOfficerId,
        Long acceptingAuthorityId,
        EmployeeAparStatus status,
        String selfAppraisalText,
        BigDecimal reportingScore,
        String reportingRemarks,
        BigDecimal reviewingScore,
        String reviewingRemarks,
        BigDecimal finalScore,
        FinalGrading finalGrading,
        String representationText,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeAparResponse from(EmployeeApar apar) {
        return new EmployeeAparResponse(
                apar.getId(),
                apar.getAparCycle().getId(),
                apar.getAparCycle().getCycleYear(),
                apar.getEmployee().getId(),
                apar.getEmployee().getEmployeeCode(),
                apar.getReportingOfficer().getId(),
                apar.getReviewingOfficer().getId(),
                apar.getAcceptingAuthority().getId(),
                apar.getStatus(),
                apar.getSelfAppraisalText(),
                apar.getReportingScore(),
                apar.getReportingRemarks(),
                apar.getReviewingScore(),
                apar.getReviewingRemarks(),
                apar.getFinalScore(),
                apar.getFinalGrading(),
                apar.getRepresentationText(),
                apar.getCreatedAt(),
                apar.getUpdatedAt()
        );
    }
}
