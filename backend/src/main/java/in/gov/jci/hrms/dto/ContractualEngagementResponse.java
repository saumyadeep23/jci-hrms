package in.gov.jci.hrms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import in.gov.jci.hrms.entity.ContractualEngagement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractualEngagementResponse(
        Long id,
        Long employeeId,
        String scaleCode,
        BigDecimal monthlyLumpsum,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate contractStartDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate contractEndDate,
        String approvalRefNo,
        String engagementTerms,
        boolean current
) {
    public static ContractualEngagementResponse from(ContractualEngagement engagement) {
        return new ContractualEngagementResponse(
                engagement.getId(),
                engagement.getEmployee().getId(),
                engagement.getGradeScale() != null ? engagement.getGradeScale().getScaleCode() : null,
                engagement.getMonthlyLumpsum(),
                engagement.getContractStartDate(),
                engagement.getContractEndDate(),
                engagement.getApprovalRefNo(),
                engagement.getEngagementTerms(),
                engagement.isCurrent());
    }
}
