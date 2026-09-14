package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfHeadMaster;

public record CpfHeadMasterResponse(String id, String code, String name, String classification, boolean interestBearing, boolean withdrawalAllowed, boolean active) {
    public static CpfHeadMasterResponse from(CpfHeadMaster h) {
        return new CpfHeadMasterResponse(h.getId().toString(), h.getCode(), h.getName(), h.getClassification(), h.isInterestBearing(), h.isWithdrawalAllowed(), h.isActive());
    }
}
