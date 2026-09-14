package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLoanRecoveryPolicy;

import java.time.LocalDate;

public record CpfLoanRecoveryPolicyResponse(
        Long id, String commencementMode, int principalInstallmentsPerInterestInstallment,
        LocalDate effectiveFrom, LocalDate effectiveTo, String remarks
) {
    public static CpfLoanRecoveryPolicyResponse from(CpfLoanRecoveryPolicy p) {
        return new CpfLoanRecoveryPolicyResponse(p.getId(), p.getCommencementMode(), p.getPrincipalInstallmentsPerInterestInstallment(),
                p.getEffectiveFrom(), p.getEffectiveTo(), p.getRemarks());
    }
}
