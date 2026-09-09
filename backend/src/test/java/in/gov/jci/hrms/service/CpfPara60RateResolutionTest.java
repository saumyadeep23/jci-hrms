package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfResolvedRateDto;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paragraph 60(2), EPF Scheme: an unnotified FY's rate falls back to the immediately preceding FY's
 * notified rate, flagged provisional - CpfRateResolutionService.resolveStatutoryRate().
 */
@SpringBootTest
@Transactional
class CpfPara60RateResolutionTest {

    @Autowired private CpfRateResolutionService rateResolutionService;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;

    // FY range picked far from any realistically pre-seeded data (the live dev DB already carries one
    // real notified row for FY 2026-2027 - see V77's own header comment) to avoid colliding with
    // cpf_statutory_interest_rates' plain UNIQUE(fin_year) constraint.

    @Test
    void resolveStatutoryRate_currentFyUnnotified_fallsBackToPrecedingFyAsProvisional() {
        rateRepository.save(new CpfStatutoryInterestRate("2035-2036", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "INT-ORD/2035-2036/01", java.time.LocalDate.of(2036, 4, 1)));
        // FY 2036-2037 deliberately left unseeded.

        CpfResolvedRateDto resolved = rateResolutionService.resolveStatutoryRate("2036-2037");

        assertThat(resolved.baseRate()).isEqualByComparingTo("8.25");
        assertThat(resolved.requestedFinYear()).isEqualTo("2036-2037");
        assertThat(resolved.effectiveFinYear()).isEqualTo("2035-2036");
        assertThat(resolved.isProvisional()).isTrue();
    }

    @Test
    void resolveStatutoryRate_currentFyNotified_returnsItDirectlyAsFinal() {
        rateRepository.save(new CpfStatutoryInterestRate("2035-2036", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "INT-ORD/2035-2036/01", java.time.LocalDate.of(2036, 4, 1)));
        rateRepository.save(new CpfStatutoryInterestRate("2036-2037", new BigDecimal("8.30"), new BigDecimal("1.00"),
                "INT-ORD/2036-2037/01", java.time.LocalDate.of(2037, 4, 1)));

        CpfResolvedRateDto resolved = rateResolutionService.resolveStatutoryRate("2036-2037");

        assertThat(resolved.baseRate()).isEqualByComparingTo("8.30");
        assertThat(resolved.effectiveFinYear()).isEqualTo("2036-2037");
        assertThat(resolved.isProvisional()).isFalse();
    }

    @Test
    void resolveStatutoryRate_neitherCurrentNorPrecedingFyNotified_throws() {
        assertThatThrownBy(() -> rateResolutionService.resolveStatutoryRate("2099-2100"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("2099-2100")
                .hasMessageContaining("2098-2099");
    }
}
