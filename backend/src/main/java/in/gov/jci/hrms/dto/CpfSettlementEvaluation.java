package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfSettlementStatus;

import java.time.LocalDate;

/**
 * Result of CpfSettlementCalculator.evaluate() - a normalized status plus a human-readable lag label
 * ("Due in 5 days", "Due today", "27 days overdue", "Settled", or "-" for NOT_APPLICABLE). Never persisted;
 * recomputed on every read against the current date, so lagDays/label automatically move day to day without
 * any scheduled job or manual update.
 */
public record CpfSettlementEvaluation(
        CpfSettlementStatus status,
        LocalDate dueDate,
        String lagLabel,
        Long lagDays
) {
    public static CpfSettlementEvaluation notApplicable() {
        return new CpfSettlementEvaluation(CpfSettlementStatus.NOT_APPLICABLE, null, "-", null);
    }
}
