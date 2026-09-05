package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HeadType;
import in.gov.jci.hrms.entity.PayrollRunType;
import in.gov.jci.hrms.entity.UnifiedSalaryHeadHistory;

import java.math.BigDecimal;

public record SalaryHistoryEntryResponse(
        Integer cycleYear,
        Integer cycleMonth,
        PayrollRunType runType,
        boolean isMigrated,
        String salaryHeadCode,
        String salaryHeadName,
        HeadType headType,
        BigDecimal amount
) {
    public static SalaryHistoryEntryResponse from(UnifiedSalaryHeadHistory entry) {
        return new SalaryHistoryEntryResponse(
                entry.getCycleYear(),
                entry.getCycleMonth(),
                entry.getRunType(),
                entry.isMigrated(),
                entry.getSalaryHeadCode(),
                entry.getSalaryHeadName(),
                entry.getHeadType(),
                entry.getAmount()
        );
    }
}
