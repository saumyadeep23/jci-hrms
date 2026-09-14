package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsCollectionBatch;
import in.gov.jci.hrms.entity.JciEccsCollectionBatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record JciEccsCollectionBatchResponse(
        Long id,
        String payrollRunId,
        String cycleCode,
        JciEccsCollectionBatchStatus batchStatus,
        Instant lockedAt,
        Instant debitConfirmedAt,
        BigDecimal totalExpectedAmount,
        BigDecimal totalDebitedAmount,
        List<JciEccsCollectionDetailResponse> details
) {
    public static JciEccsCollectionBatchResponse from(JciEccsCollectionBatch batch, List<JciEccsCollectionDetailResponse> details) {
        return new JciEccsCollectionBatchResponse(batch.getId(), batch.getPayrollRunId(), batch.getCycle().getCycleCode(),
                batch.getBatchStatus(), batch.getLockedAt(), batch.getDebitConfirmedAt(), batch.getTotalExpectedAmount(),
                batch.getTotalDebitedAmount(), details);
    }
}
