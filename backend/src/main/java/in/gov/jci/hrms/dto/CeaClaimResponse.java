package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.CeaClaimType;
import in.gov.jci.hrms.entity.EmployeeCeaClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CeaClaimResponse(
        Long id,
        String claimNo,
        Long employeeId,
        String employeeCode,
        String fullName,
        Long dependentId,
        String dependentName,
        String academicYear,
        CeaClaimType claimType,
        String schoolName,
        String schoolRegNo,
        String standardClass,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal claimedAmount,
        BigDecimal admissibleAmount,
        BigDecimal passedAmount,
        CeaClaimStatus claimStatus,
        Long verifiedByOfficerId,
        Instant verifiedAt,
        String sanctionOrderNo,
        LocalDate sanctionDate,
        Long sanctionedByOfficerId,
        String billNo,
        LocalDate billDate,
        Long passedByOfficerId,
        Instant passedAt,
        boolean isPayrollProcessed,
        Long payrollBatchId,
        String supportingDocRef,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static CeaClaimResponse from(EmployeeCeaClaim claim) {
        return new CeaClaimResponse(
                claim.getId(),
                claim.getClaimNo(),
                claim.getEmployee().getId(),
                claim.getEmployee().getEmployeeCode(),
                claim.getEmployee().getFullName(),
                claim.getDependent().getId(),
                claim.getDependent().getName(),
                claim.getAcademicYear(),
                claim.getClaimType(),
                claim.getSchoolName(),
                claim.getSchoolRegNo(),
                claim.getStandardClass(),
                claim.getPeriodFrom(),
                claim.getPeriodTo(),
                claim.getClaimedAmount(),
                claim.getAdmissibleAmount(),
                claim.getPassedAmount(),
                claim.getClaimStatus(),
                claim.getVerifiedByOfficer() != null ? claim.getVerifiedByOfficer().getId() : null,
                claim.getVerifiedAt(),
                claim.getSanctionOrderNo(),
                claim.getSanctionDate(),
                claim.getSanctionedByOfficer() != null ? claim.getSanctionedByOfficer().getId() : null,
                claim.getBillNo(),
                claim.getBillDate(),
                claim.getPassedByOfficer() != null ? claim.getPassedByOfficer().getId() : null,
                claim.getPassedAt(),
                claim.isPayrollProcessed(),
                claim.getPayrollBatch() != null ? claim.getPayrollBatch().getId() : null,
                claim.getSupportingDocRef(),
                claim.getRejectionReason(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
