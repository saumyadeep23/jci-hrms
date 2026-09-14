package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.service.JciEccsIntegrityCheckService;

import java.time.Instant;

public record JciEccsIntegrityCheckResultResponse(
        String checkType,
        String entityType,
        Long entityId,
        JciEccsIntegrityCheckService.Severity severity,
        String message,
        String expectedValue,
        String actualValue,
        Instant detectedAt
) {
    public static JciEccsIntegrityCheckResultResponse from(JciEccsIntegrityCheckService.IntegrityCheckResult r) {
        return new JciEccsIntegrityCheckResultResponse(r.checkType(), r.entityType(), r.entityId(), r.severity(), r.message(),
                r.expectedValue(), r.actualValue(), r.detectedAt());
    }
}
