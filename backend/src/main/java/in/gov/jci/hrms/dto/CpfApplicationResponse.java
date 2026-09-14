package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfApplication;
import in.gov.jci.hrms.entity.CpfApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CpfApplicationResponse(
        String id,
        String applicationNumber,
        String employeeCode,
        String purposeCode,
        String ruleVersionId,
        String ruleVersionTag,
        CpfApplicationStatus status,
        BigDecimal appliedAmount,
        BigDecimal eligibleAmount,
        BigDecimal sanctionedAmount,
        Integer tenureMonths,
        BigDecimal calculatedEmi,
        String calculationTrace,
        /** JSON array of CpfApplicationDocumentSubmission, same raw-string convention as calculationTrace above. */
        String submittedDocuments,
        Instant createdAt,
        Instant sanctionedAt,
        Instant disbursedAt,
        /** Part 7/38 - the cpf_loan_applications.id this application's disbursement bridged into, when its purpose is refundable. Null for non-refundable withdrawals/final settlements, which never create a loan. */
        Long linkedLoanId
) {
    public static CpfApplicationResponse from(CpfApplication a) {
        return from(a, null);
    }

    public static CpfApplicationResponse from(CpfApplication a, Long linkedLoanId) {
        return new CpfApplicationResponse(a.getId().toString(), a.getApplicationNumber(), a.getEmployeeCode(), a.getPurpose().getCode(),
                a.getRuleVersion().getId().toString(), a.getRuleVersion().getVersionTag(), a.getStatus(), a.getAppliedAmount(),
                a.getEligibleAmount(), a.getSanctionedAmount(), a.getTenureMonths(), a.getCalculatedEmi(), a.getCalculationTrace(),
                a.getSubmittedDocuments(), a.getCreatedAt(), a.getSanctionedAt(), a.getDisbursedAt(), linkedLoanId);
    }
}
