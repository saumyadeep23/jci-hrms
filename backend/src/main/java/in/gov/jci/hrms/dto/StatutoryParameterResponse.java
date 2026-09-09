package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.entity.StatutoryParamValueType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StatutoryParameterResponse(
        Long id,
        String paramKey,
        String paramName,
        BigDecimal paramValue,
        StatutoryParamValueType valType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String remarks,
        Instant updatedAt
) {
    public static StatutoryParameterResponse from(PayrollStatutoryParameter param) {
        return new StatutoryParameterResponse(
                param.getId(),
                param.getParamKey(),
                param.getParamName(),
                param.getParamValue(),
                param.getValType(),
                param.getEffectiveFrom(),
                param.getEffectiveTo(),
                param.getRemarks(),
                param.getUpdatedAt());
    }
}
