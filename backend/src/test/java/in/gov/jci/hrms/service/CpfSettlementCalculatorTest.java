package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.CpfTrustPolicyProperties;
import in.gov.jci.hrms.dto.CpfSettlementEvaluation;
import in.gov.jci.hrms.entity.CpfSettlementStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * calculateSettlementDueDate()/calculateSettlementStatus() - the automatic, never-manually-entered
 * settlement due-date/lag rule behind the CPF Trust Members' List's Due/Lag column. A pure unit test (no
 * Spring context needed) since CpfSettlementCalculator has no dependency beyond the policy properties.
 */
class CpfSettlementCalculatorTest {

    private final CpfTrustPolicyProperties properties = new CpfTrustPolicyProperties();
    private final CpfSettlementCalculator calculator = new CpfSettlementCalculator(properties);

    @Test
    void calculateSettlementDueDate_isSeparationDatePlusConfiguredDays() {
        properties.setSettlementDueDays(30);
        LocalDate separationDate = LocalDate.of(2026, 7, 15);

        assertThat(calculator.calculateSettlementDueDate(separationDate)).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void calculateSettlementDueDate_nullSeparationDate_isNull() {
        assertThat(calculator.calculateSettlementDueDate(null)).isNull();
    }

    @Test
    void calculateSettlementStatus_activeMember_isNotApplicable() {
        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(null, false, false, LocalDate.now());

        assertThat(evaluation.status()).isEqualTo(CpfSettlementStatus.NOT_APPLICABLE);
        assertThat(evaluation.dueDate()).isNull();
        assertThat(evaluation.lagLabel()).isEqualTo("-");
    }

    @Test
    void calculateSettlementStatus_disbursed_isSettledRegardlessOfDate() {
        LocalDate separationDate = LocalDate.of(2020, 1, 1);

        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(separationDate, true, true, LocalDate.now());

        assertThat(evaluation.status()).isEqualTo(CpfSettlementStatus.SETTLED);
        assertThat(evaluation.lagLabel()).isEqualTo("Settled");
        assertThat(evaluation.lagDays()).isNull();
    }

    @Test
    void calculateSettlementStatus_beforeDueDate_pendingWithDueInXDays() {
        properties.setSettlementDueDays(30);
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalDate separationDate = today.minusDays(10);

        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(separationDate, false, false, today);

        assertThat(evaluation.status()).isEqualTo(CpfSettlementStatus.PENDING);
        assertThat(evaluation.lagLabel()).isEqualTo("Due in 20 days");
    }

    @Test
    void calculateSettlementStatus_onDueDate_dueToday() {
        properties.setSettlementDueDays(30);
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalDate separationDate = today.minusDays(30);

        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(separationDate, false, false, today);

        assertThat(evaluation.lagLabel()).isEqualTo("Due today");
        assertThat(evaluation.lagDays()).isZero();
    }

    @Test
    void calculateSettlementStatus_pastDueDateNoTerminalSettlement_overdue() {
        properties.setSettlementDueDays(30);
        LocalDate today = LocalDate.of(2026, 9, 9);
        // Separation 15 Jul 2026 + 30 days = due 14 Aug 2026; today 9 Sep 2026 is 26 days past due.
        LocalDate separationDate = LocalDate.of(2026, 7, 15);

        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(separationDate, false, false, today);

        assertThat(evaluation.status()).isEqualTo(CpfSettlementStatus.OVERDUE);
        assertThat(evaluation.lagDays()).isEqualTo(26L);
        assertThat(evaluation.lagLabel()).isEqualTo("26 days overdue");
    }

    @Test
    void calculateSettlementStatus_lagIncrementsAutomaticallyWithToday() {
        properties.setSettlementDueDays(30);
        LocalDate separationDate = LocalDate.of(2026, 7, 15);

        CpfSettlementEvaluation today = calculator.calculateSettlementStatus(separationDate, false, false, LocalDate.of(2026, 9, 9));
        CpfSettlementEvaluation tomorrow = calculator.calculateSettlementStatus(separationDate, false, false, LocalDate.of(2026, 9, 10));

        assertThat(today.lagDays()).isEqualTo(26L);
        assertThat(tomorrow.lagDays()).isEqualTo(27L);
    }

    @Test
    void calculateSettlementStatus_pastDueDateWithTerminalSettlementStarted_isInProcessNotPending() {
        properties.setSettlementDueDays(30);
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalDate separationDate = today.minusDays(10);

        CpfSettlementEvaluation evaluation = calculator.calculateSettlementStatus(separationDate, false, true, today);

        assertThat(evaluation.status()).isEqualTo(CpfSettlementStatus.IN_PROCESS);
    }
}
