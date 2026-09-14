package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfWithdrawalPurposeMaster;

public record CpfWithdrawalPurposeResponse(String id, String code, String name, String description, String typeCode, boolean active) {
    public static CpfWithdrawalPurposeResponse from(CpfWithdrawalPurposeMaster p) {
        return new CpfWithdrawalPurposeResponse(p.getId().toString(), p.getCode(), p.getName(), p.getDescription(), p.getType().getCode(), p.isActive());
    }
}
