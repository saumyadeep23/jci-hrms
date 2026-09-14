package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AuditLog;

import java.time.Instant;

/** One row of a dispute's status-change timeline (Part 41) - reuses this app's existing generic audit_logs table (CpfTransactionDispute is Auditable), never a bespoke dispute-history table. */
public record CpfDisputeHistoryEntryResponse(String action, String performedBy, Instant timestamp, String previousState, String newState) {
    public static CpfDisputeHistoryEntryResponse from(AuditLog log) {
        return new CpfDisputeHistoryEntryResponse(log.getAction().name(), log.getActingUsername(), log.getCreatedAt(),
                log.getBeforeState(), log.getAfterState());
    }
}
