package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollBatch;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Summary returned by the batch create/list/calculate/finalize endpoints - PayrollBatch's own aggregate roll-up totals. */
public record PayrollBatchResponse(
        Long id,
        String batchNo,
        int salMonth,
        int salYear,
        String financialYear,
        String status,
        String batchType,
        LocalDate payDate,
        int totalEmployees,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet) {

    public static PayrollBatchResponse from(PayrollBatch batch) {
        return new PayrollBatchResponse(batch.getId(), batch.getBatchNo(), batch.getSalMonth(), batch.getSalYear(),
                batch.getFinancialYear(), batch.getStatus().name(), batch.getBatchType().name(), batch.getPayDate(),
                batch.getTotalEmployees(), batch.getTotalGross(), batch.getTotalDeductions(), batch.getTotalNet());
    }
}
