package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.MedicalClaim;
import in.gov.jci.hrms.entity.ReimbursementClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MedicalClaimResponse(
        Long id,
        String claimNumber,
        Long employeeId,
        String employeeCode,
        Long dependentId,
        String dependentName,
        BigDecimal totalClaimedAmount,
        BigDecimal totalAllowedAmount,
        ReimbursementClaimStatus status,
        LocalDate submissionDate,
        String verifiedBy,
        String approvedByFinance,
        Instant createdAt,
        Instant updatedAt,
        List<MedicalClaimItemResponse> items
) {
    public static MedicalClaimResponse from(MedicalClaim claim, List<MedicalClaimItemResponse> items) {
        EmployeeDependent dependent = claim.getDependent();
        return new MedicalClaimResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getEmployee().getId(),
                claim.getEmployee().getEmployeeCode(),
                dependent != null ? dependent.getId() : null,
                dependent != null ? dependent.getName() : null,
                claim.getTotalClaimedAmount(),
                claim.getTotalAllowedAmount(),
                claim.getStatus(),
                claim.getSubmissionDate(),
                claim.getVerifiedBy(),
                claim.getApprovedByFinance(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                items
        );
    }
}
