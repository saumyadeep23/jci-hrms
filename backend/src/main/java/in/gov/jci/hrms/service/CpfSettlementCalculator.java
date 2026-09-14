package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.CpfTrustPolicyProperties;
import in.gov.jci.hrms.dto.CpfSettlementEvaluation;
import in.gov.jci.hrms.entity.CpfSettlementStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The single source of truth for "when is this member's CPF settlement due, and how late is it" - every
 * caller (the members' list, the member 360 drawer, any future settlement worklist) must go through this
 * rather than each computing its own due-date/lag arithmetic, so the rule only ever lives in one place and
 * stays in sync with CpfTrustPolicyProperties.settlementDueDays.
 *
 * Never persisted: dueDate/lagDays/status are recomputed against "today" on every call, so a lag figure
 * shown yesterday and shown again today reflects the day that actually passed, with no batch job needed to
 * "roll it forward".
 */
@Service
public class CpfSettlementCalculator {

    private final CpfTrustPolicyProperties policyProperties;

    public CpfSettlementCalculator(CpfTrustPolicyProperties policyProperties) {
        this.policyProperties = policyProperties;
    }

    /** null if separationDate is null (member hasn't separated - settlement isn't due at all). */
    public LocalDate calculateSettlementDueDate(LocalDate separationDate) {
        if (separationDate == null) {
            return null;
        }
        return separationDate.plusDays(policyProperties.getSettlementDueDays());
    }

    /**
     * @param separationDate        null for an active member - short-circuits to NOT_APPLICABLE.
     * @param isDisbursed            true once the member's terminal settlement has actually reached DISBURSED.
     * @param terminalSettlementStarted true if a terminal_settlements row exists at all (DRAFT/AUDITED/APPROVED) -
     *                               distinguishes PENDING (nothing started yet) from IN_PROCESS.
     */
    public CpfSettlementEvaluation calculateSettlementStatus(LocalDate separationDate, boolean isDisbursed,
                                                               boolean terminalSettlementStarted, LocalDate today) {
        if (separationDate == null) {
            return CpfSettlementEvaluation.notApplicable();
        }
        if (isDisbursed) {
            return new CpfSettlementEvaluation(CpfSettlementStatus.SETTLED, calculateSettlementDueDate(separationDate), "Settled", null);
        }

        LocalDate dueDate = calculateSettlementDueDate(separationDate);
        long daysPastDue = ChronoUnit.DAYS.between(dueDate, today);

        if (daysPastDue > 0) {
            return new CpfSettlementEvaluation(CpfSettlementStatus.OVERDUE, dueDate, daysPastDue + " day" + (daysPastDue == 1 ? "" : "s") + " overdue", daysPastDue);
        }
        if (daysPastDue == 0) {
            CpfSettlementStatus status = terminalSettlementStarted ? CpfSettlementStatus.IN_PROCESS : CpfSettlementStatus.PENDING;
            return new CpfSettlementEvaluation(status, dueDate, "Due today", 0L);
        }
        long daysUntilDue = -daysPastDue;
        CpfSettlementStatus status = terminalSettlementStarted ? CpfSettlementStatus.IN_PROCESS : CpfSettlementStatus.PENDING;
        return new CpfSettlementEvaluation(status, dueDate, "Due in " + daysUntilDue + " day" + (daysUntilDue == 1 ? "" : "s"), -daysUntilDue);
    }
}
