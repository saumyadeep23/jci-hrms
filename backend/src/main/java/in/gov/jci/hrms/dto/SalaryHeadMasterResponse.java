package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.SalaryHeadMaster;

import java.time.Instant;

public record SalaryHeadMasterResponse(
        Long id,
        String code,
        String name,
        HeadType headType,
        String glCode,
        boolean isVariable,
        boolean isTaxable,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static SalaryHeadMasterResponse from(SalaryHeadMaster head) {
        return new SalaryHeadMasterResponse(
                head.getId(),
                head.getCode(),
                head.getName(),
                head.getHeadType(),
                head.getGlCode(),
                head.isVariable(),
                head.isTaxable(),
                head.isActive(),
                head.getCreatedAt(),
                head.getUpdatedAt()
        );
    }
}
