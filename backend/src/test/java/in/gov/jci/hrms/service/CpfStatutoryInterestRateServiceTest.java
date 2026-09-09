package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfStatutoryInterestRateRequest;
import in.gov.jci.hrms.dto.CpfStatutoryInterestRateResponse;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CPF Rate of Interest Entry - the GUI's backing engine. FY range picked far from the one real notified
 * row the live dev DB already carries (FY 2026-2027 - see V77's own header comment) so it never collides
 * with cpf_statutory_interest_rates' UNIQUE(fin_year).
 */
@SpringBootTest
@Transactional
class CpfStatutoryInterestRateServiceTest {

    @Autowired private CpfStatutoryInterestRateService rateService;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void create_computesEffectiveLoanRateAndFyBoundsServerSide() {
        CpfStatutoryInterestRateResponse created = rateService.create(new CpfStatutoryInterestRateRequest(
                "2045-2046", new BigDecimal("8.25"), new BigDecimal("1.00"), "EPFO/RATIF/2045-46/01", LocalDate.of(2046, 4, 1)));

        assertThat(created.effectiveLoanRate()).isEqualByComparingTo("9.25");
        assertThat(created.effectiveFrom()).isEqualTo(LocalDate.of(2045, 4, 1));
        assertThat(created.effectiveTo()).isEqualTo(LocalDate.of(2046, 3, 31));
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void create_duplicateFinYear_rejectedWithConflict() {
        rateService.create(new CpfStatutoryInterestRateRequest(
                "2046-2047", new BigDecimal("8.25"), new BigDecimal("1.00"), "EPFO/RATIF/2046-47/01", LocalDate.of(2047, 4, 1)));

        assertThatThrownBy(() -> rateService.create(new CpfStatutoryInterestRateRequest(
                "2046-2047", new BigDecimal("8.30"), new BigDecimal("1.00"), "EPFO/RATIF/2046-47/02", LocalDate.of(2047, 5, 1))))
                .isInstanceOf(MasterDataConflictException.class)
                .hasMessageContaining("2046-2047");
    }

    @Test
    void list_returnsNewestFinYearFirst() {
        rateService.create(new CpfStatutoryInterestRateRequest(
                "2047-2048", new BigDecimal("8.10"), new BigDecimal("1.00"), "EPFO/RATIF/2047-48/01", LocalDate.of(2048, 4, 1)));
        rateService.create(new CpfStatutoryInterestRateRequest(
                "2048-2049", new BigDecimal("8.15"), new BigDecimal("1.00"), "EPFO/RATIF/2048-49/01", LocalDate.of(2049, 4, 1)));

        List<CpfStatutoryInterestRateResponse> list = rateService.list();

        int idx2048 = list.indexOf(list.stream().filter(r -> r.finYear().equals("2048-2049")).findFirst().orElseThrow());
        int idx2047 = list.indexOf(list.stream().filter(r -> r.finYear().equals("2047-2048")).findFirst().orElseThrow());
        assertThat(idx2048).isLessThan(idx2047);
    }

    @Test
    void create_isPickedUpByTheGenericAuditLog() {
        CpfStatutoryInterestRateResponse created = rateService.create(new CpfStatutoryInterestRateRequest(
                "2049-2050", new BigDecimal("8.20"), new BigDecimal("1.00"), "EPFO/RATIF/2049-50/01", LocalDate.of(2050, 4, 1)));

        var logs = auditLogRepository.findAll().stream()
                .filter(l -> "CpfStatutoryInterestRate".equals(l.getEntityName()) && created.id().equals(l.getEntityId()))
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction().name()).isEqualTo("CREATE");
        assertThat(logs.get(0).getAfterState()).contains("2049-2050");
    }
}
