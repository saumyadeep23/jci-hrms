package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * One employee's computed annual increment - GET /api/v1/increments/due-list.
 * 3% of current regular_basic_pay, rounded UP to the next higher multiple
 * of Rs. 10 (standard IDA increment rounding rule). isWithheld/
 * withheldReason flags an active disciplinary case; the batch endpoint
 * (IncrementProcessingService.processBatch) also enforces this
 * server-side and skips a withheld employee even if included in the
 * request, rather than relying solely on the caller having excluded them.
 */
public record IncrementDueEntry(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String payScaleGrade,
        BigDecimal currentBasicPay,
        BigDecimal incrementAmount,
        BigDecimal newBasicPay,
        boolean atStagnationCeiling,
        boolean isWithheld,
        String withheldReason
) {
}
