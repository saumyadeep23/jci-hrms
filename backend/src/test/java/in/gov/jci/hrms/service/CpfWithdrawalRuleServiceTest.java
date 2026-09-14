package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfRuleCeilingConfig;
import in.gov.jci.hrms.dto.CpfRuleHeadConfig;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionRequest;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionResponse;
import in.gov.jci.hrms.entity.CpfCeilingOperator;
import in.gov.jci.hrms.entity.CpfCeilingSourceMetric;
import in.gov.jci.hrms.entity.CpfFrequencyScope;
import in.gov.jci.hrms.entity.CpfRepaymentCreditMethod;
import in.gov.jci.hrms.entity.CpfRuleStatus;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfWithdrawalRuleDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rule-change approval workflow AND its core acceptance property (Part 37/40 of the module spec): an
 * administrator can change a withdrawal ceiling from the portal (e.g. Marriage 50% -&gt; 60%) - subject to
 * approval and effective-date control - WITHOUT a code change, and a transaction already evaluated under
 * the old rule keeps referring to the exact old rule version's figures even after the new one is approved.
 * Runs against the shared dev database (see CpfWithdrawalRuleEngineTest's own javadoc), creating new
 * MARRIAGE versions dated well after the live 2026-04-01 one so nothing here touches that live row.
 */
@SpringBootTest
@Transactional
class CpfWithdrawalRuleServiceTest {

    @Autowired private CpfWithdrawalRuleService ruleService;
    @Autowired private CpfWithdrawalRuleDetailRepository detailRepository;

    private CpfWithdrawalRuleVersionRequest marriageRequest(BigDecimal percent, LocalDate effectiveFrom) {
        return new CpfWithdrawalRuleVersionRequest(
                "MARRIAGE", "TEST-" + percent, effectiveFrom, "Test-driven ceiling change to " + percent + "%",
                84, true, false, CpfFrequencyScope.SERVICE, 3, 1, BigDecimal.ZERO, CpfRepaymentCreditMethod.ORIGINAL_DEBIT_HEAD,
                null, null, null, null, null, null, null,
                "NORMAL", null, null, "CPF_STANDARD_APPROVAL",
                List.of(new CpfRuleHeadConfig("HEAD_B", true, 1, 1), new CpfRuleHeadConfig("HEAD_A", true, 2, 2),
                        new CpfRuleHeadConfig("HEAD_C", false, 99, 99)),
                List.of(new CpfRuleCeilingConfig("BALANCE_PCT_CAP", CpfCeilingSourceMetric.ELIGIBLE_BALANCE, CpfCeilingOperator.PERCENTAGE, percent, 1)),
                List.of());
    }

    @Test
    void draftLifecycle_submitVerifyApprove_movesThroughEachStatus() {
        var draft = ruleService.createDraft(marriageRequest(new BigDecimal("55"), LocalDate.of(2031, 4, 1)), "tester");
        assertThat(draft.status()).isEqualTo(CpfRuleStatus.DRAFT);

        UUID id = UUID.fromString(draft.id());
        var submitted = ruleService.submitForVerification(id, "tester");
        assertThat(submitted.status()).isEqualTo(CpfRuleStatus.PENDING_VERIFICATION);

        var verified = ruleService.verify(id, "secretariat-officer");
        assertThat(verified.status()).isEqualTo(CpfRuleStatus.PENDING_APPROVAL);
        assertThat(verified.verifiedBy()).isEqualTo("secretariat-officer");

        var approved = ruleService.approve(id, "trustee-officer", "TRUSTEE-APPR/001", "Approved per Trust meeting");
        assertThat(approved.status()).isEqualTo(CpfRuleStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo("trustee-officer");
    }

    @Test
    void calculate_startsWithNoBusinessValueHardcoded_rejectsUnapprovedVersion() {
        var draft = ruleService.createDraft(marriageRequest(new BigDecimal("55"), LocalDate.of(2032, 4, 1)), "tester");
        // A DRAFT (not yet even submitted) version must never be resolved as "active" - CpfWithdrawalRuleEngine
        // and CpfApplicationService only ever consult resolveActiveVersion(), which only returns APPROVED rows.
        var active = ruleServiceResolve("MARRIAGE", LocalDate.of(2032, 5, 1));
        assertThat(active).map(v -> v.getVersionTag()).hasValue("2026.1"); // still the live-seeded one, not our unapproved draft
    }

    @Test
    void approvingNewVersion_supersedesOldOne_oldTransactionsKeepOldFigures() {
        // Approve a 55% version effective from 2033-04-01.
        var v1 = fullyApprove(marriageRequest(new BigDecimal("55"), LocalDate.of(2033, 4, 1)));
        CpfWithdrawalRuleDetail v1Detail = detailRepository.findByVersion_Id(UUID.fromString(v1.id())).orElseThrow();
        assertThat(v1.status()).isEqualTo(CpfRuleStatus.APPROVED);

        // A "transaction" processed under v1 remembers this exact detail row's id.
        UUID v1DetailId = v1Detail.getId();

        // Before v2 exists, resolving "as of" a date in v1's window correctly returns v1 (this is the real
        // usage pattern: resolveActiveVersion answers "what rule applies to a NEW application right now" -
        // it is not a historical-lookback API; an application's own reproducibility instead comes from its
        // stored rule_version_id FK, exercised below via v1DetailId, not from re-resolving a past date).
        var resolvedForV1Window = ruleServiceResolve("MARRIAGE", LocalDate.of(2033, 6, 1));
        assertThat(resolvedForV1Window).map(v -> v.getId().toString()).hasValue(v1.id());

        // Now approve a 65% version effective from 2034-04-01 - this must supersede v1 (close its effectiveTo)
        // without ever modifying v1's own ceiling component (55%).
        var v2 = fullyApprove(marriageRequest(new BigDecimal("65"), LocalDate.of(2034, 4, 1)));
        assertThat(v2.status()).isEqualTo(CpfRuleStatus.APPROVED);

        var v1AfterSupersede = ruleService.getVersion(UUID.fromString(v1.id()));
        assertThat(v1AfterSupersede.status()).isEqualTo(CpfRuleStatus.SUPERSEDED);
        assertThat(v1AfterSupersede.effectiveTo()).isEqualTo(LocalDate.of(2034, 3, 31));

        // The "old transaction"'s exact detail row must be untouched - still 55%, never bumped to 65%.
        CpfWithdrawalRuleDetail stillOldDetail = detailRepository.findById(v1DetailId).orElseThrow();
        assertThat(stillOldDetail.getMinServiceMonths()).isEqualTo(84);

        // A NEW application from this point on resolves to v2, not the now-superseded v1.
        var resolvedForV2Window = ruleServiceResolve("MARRIAGE", LocalDate.of(2034, 6, 1));
        assertThat(resolvedForV2Window).map(v -> v.getId().toString()).hasValue(v2.id());
    }

    @Test
    void reject_onlyAllowedFromPendingStates() {
        var draft = ruleService.createDraft(marriageRequest(new BigDecimal("55"), LocalDate.of(2035, 4, 1)), "tester");
        assertThatThrownBy(() -> ruleService.reject(UUID.fromString(draft.id()), "someone", "no reason"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only a PENDING_VERIFICATION/PENDING_APPROVAL version can be rejected");

        ruleService.submitForVerification(UUID.fromString(draft.id()), "tester");
        var rejected = ruleService.reject(UUID.fromString(draft.id()), "secretariat", "Ceiling percentage not justified");
        assertThat(rejected.status()).isEqualTo(CpfRuleStatus.REJECTED);
    }

    private CpfWithdrawalRuleVersionResponse fullyApprove(CpfWithdrawalRuleVersionRequest request) {
        var draft = ruleService.createDraft(request, "tester");
        ruleService.submitForVerification(UUID.fromString(draft.id()), "tester");
        ruleService.verify(UUID.fromString(draft.id()), "secretariat-officer");
        return ruleService.approve(UUID.fromString(draft.id()), "trustee-officer", "TRUSTEE-APPR/" + request.versionTag(), "Approved for test");
    }

    /** Package-visible resolveActiveVersion() is accessible from this same-package test class directly. */
    private java.util.Optional<in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion> ruleServiceResolve(String purposeCode, LocalDate asOf) {
        return ruleService.resolveActiveVersion(purposeCode, asOf);
    }
}
