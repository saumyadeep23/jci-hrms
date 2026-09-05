package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ReimbursementClaimStatus;
import in.gov.jci.hrms.entity.TadaClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TadaClaimResponse(
        Long id,
        Long tourRequestId,
        String claimNumber,
        Long employeeId,
        String employeeCode,
        BigDecimal outOfPocketClaimed,
        BigDecimal outOfPocketAllowed,
        ReimbursementClaimStatus status,
        String verifiedBy,
        String approvedByFinance,
        LocalDate submissionDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static TadaClaimResponse from(TadaClaim claim) {
        return new TadaClaimResponse(
                claim.getId(),
                claim.getTourRequest().getId(),
                claim.getClaimNumber(),
                claim.getEmployee().getId(),
                claim.getEmployee().getEmployeeCode(),
                claim.getOutOfPocketClaimed(),
                claim.getOutOfPocketAllowed(),
                claim.getStatus(),
                claim.getVerifiedBy(),
                claim.getApprovedByFinance(),
                claim.getSubmissionDate(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
