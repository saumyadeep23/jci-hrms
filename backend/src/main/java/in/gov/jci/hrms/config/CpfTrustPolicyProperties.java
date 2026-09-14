package in.gov.jci.hrms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CPF Trust administrative policy constants that, unlike the statutory interest rate (notified via
 * {@code cpf_statutory_interest_rates} and resolved by CpfRateResolutionService), have no authoritative
 * source anywhere in this codebase or the SRS it was built against.
 *
 * settlementDueDays (default 30) is a placeholder operating target for how long CPF Trust settlement should
 * take after an employee's separation date, NOT a confirmed JCI Trust Rule or statutory deadline - override
 * via cpf.trust.settlement-due-days in application.yml once the Trust's own settlement-turnaround policy is
 * known; no code change needed to do so.
 *
 * epsMinimumPensionableServiceYears (default 10) mirrors the well-known EPS-95 Para 12 minimum-pensionable-
 * service threshold for monthly-pension eligibility (below it, a member draws a withdrawal benefit instead),
 * but is left configurable here rather than hardcoded in the eligibility service since JCI's own scheme
 * rules (and any organization-specific variation) have not been confirmed against this codebase.
 */
@Component
@ConfigurationProperties(prefix = "cpf.trust")
public class CpfTrustPolicyProperties {

    private int settlementDueDays = 30;
    private int epsMinimumPensionableServiceYears = 10;

    public int getSettlementDueDays() {
        return settlementDueDays;
    }

    public void setSettlementDueDays(int settlementDueDays) {
        this.settlementDueDays = settlementDueDays;
    }

    public int getEpsMinimumPensionableServiceYears() {
        return epsMinimumPensionableServiceYears;
    }

    public void setEpsMinimumPensionableServiceYears(int epsMinimumPensionableServiceYears) {
        this.epsMinimumPensionableServiceYears = epsMinimumPensionableServiceYears;
    }
}
