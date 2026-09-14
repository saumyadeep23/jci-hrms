package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.service.JciEccsReconciliationService;

import java.util.Map;

public record JciEccsReconciliationSummaryResponse(String payrollRunId, int totalLines, Map<JciEccsReconciliationStatus, Integer> countsByStatus) {
    public static JciEccsReconciliationSummaryResponse from(JciEccsReconciliationService.ReconciliationSummary summary) {
        return new JciEccsReconciliationSummaryResponse(summary.payrollRunId(), summary.totalLines(), summary.countsByStatus());
    }
}
