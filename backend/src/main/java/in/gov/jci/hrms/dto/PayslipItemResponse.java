package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayslipItem;

import java.math.BigDecimal;

public record PayslipItemResponse(
        Long id,
        Long salaryHeadId,
        String salaryHeadCode,
        String salaryHeadName,
        BigDecimal amount,
        String causeRemarks
) {
    public static PayslipItemResponse from(PayslipItem item) {
        return new PayslipItemResponse(
                item.getId(),
                item.getSalaryHead().getId(),
                item.getSalaryHead().getCode(),
                item.getSalaryHead().getName(),
                item.getAmount(),
                item.getCauseRemarks()
        );
    }
}
