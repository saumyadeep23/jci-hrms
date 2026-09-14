package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfPayrollDeductionCap;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CpfPayrollDeductionCapResponse(
        Long id, BigDecimal normalPercent, BigDecimal cooperativePercent, String applicability, String legalReference,
        LocalDate effectiveFrom, LocalDate effectiveTo, String remarks
) {
    public static CpfPayrollDeductionCapResponse from(CpfPayrollDeductionCap c) {
        return new CpfPayrollDeductionCapResponse(c.getId(), c.getNormalPercent(), c.getCooperativePercent(), c.getApplicability(),
                c.getLegalReference(), c.getEffectiveFrom(), c.getEffectiveTo(), c.getRemarks());
    }
}
