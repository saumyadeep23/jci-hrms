package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.StatutoryHead;

public record StatutoryHeadResponse(
        Integer statHeadCount,
        String statHeadDescr,
        String statHeadShortName
) {
    public static StatutoryHeadResponse from(StatutoryHead head) {
        return new StatutoryHeadResponse(
                head.getStatHeadCount(),
                head.getStatHeadDescr(),
                head.getStatHeadShortName());
    }
}
