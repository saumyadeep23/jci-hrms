package in.gov.jci.hrms.dto;

import java.util.List;

/** GET /api/v1/payroll/batches/{id}/details - the batch summary plus every employee's monthly record, each with the full zero-filled head matrix (PayrollMonthlyRecordResponse.fullHeadLines()). */
public record PayrollBatchDetailsResponse(PayrollBatchResponse batch, List<PayrollMonthlyRecordResponse> records) {
}
