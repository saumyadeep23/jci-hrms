package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.BeneficiaryType;
import in.gov.jci.hrms.entity.TerminalSettlementBeneficiary;

import java.math.BigDecimal;

public record TerminalSettlementBeneficiaryResponse(
        Long id,
        BeneficiaryType beneficiaryType,
        String beneficiaryName,
        String relationship,
        BigDecimal sharePercentage,
        BigDecimal allocatedAmount,
        String bankAccountNo,
        String bankIfsc,
        String bankName,
        String panNumber
) {
    public static TerminalSettlementBeneficiaryResponse from(TerminalSettlementBeneficiary beneficiary) {
        return new TerminalSettlementBeneficiaryResponse(
                beneficiary.getId(), beneficiary.getBeneficiaryType(), beneficiary.getBeneficiaryName(),
                beneficiary.getRelationship(), beneficiary.getSharePercentage(), beneficiary.getAllocatedAmount(),
                beneficiary.getBankAccountNo(), beneficiary.getBankIfsc(), beneficiary.getBankName(), beneficiary.getPanNumber());
    }
}
