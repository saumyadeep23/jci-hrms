package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.SalaryHead;
import in.gov.jci.hrms.entity.SalaryHeadEffectType;

public record SalaryHeadResponse(
        Integer headCount,
        String description,
        String shortName,
        SalaryHeadEffectType effectType,
        boolean isVariable,
        String applicableFor,
        Integer salSlipVis,
        boolean basicDependent,
        String refAccountCode
) {
    public static SalaryHeadResponse from(SalaryHead head) {
        return new SalaryHeadResponse(
                head.getHeadCount(),
                head.getDescription(),
                head.getShortName(),
                head.getEffectType(),
                head.isVariable(),
                head.getApplicableFor(),
                head.getSalSlipVis(),
                head.isBasicDependent(),
                head.getRefAccountCode());
    }
}
