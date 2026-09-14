package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfWithdrawalTypeMaster;

public record CpfWithdrawalTypeResponse(String id, String code, String name, boolean refundable, boolean settlement, boolean active) {
    public static CpfWithdrawalTypeResponse from(CpfWithdrawalTypeMaster t) {
        return new CpfWithdrawalTypeResponse(t.getId().toString(), t.getCode(), t.getName(), t.isRefundable(), t.isSettlement(), t.isActive());
    }
}
