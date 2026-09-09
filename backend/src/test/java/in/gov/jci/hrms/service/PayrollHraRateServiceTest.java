package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollHraRateRequest;
import in.gov.jci.hrms.dto.PayrollHraRateResponse;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollHraRateServiceTest {

    @Mock
    private PayrollHraRateRepository payrollHraRateRepository;

    private PayrollHraRateService payrollHraRateService;

    @BeforeEach
    void setUp() {
        payrollHraRateService = new PayrollHraRateServiceImpl(payrollHraRateRepository);
    }

    private PayrollHraRateRequest validRequest() {
        return new PayrollHraRateRequest("X", new BigDecimal("30.00"), new BigDecimal("6750.00"),
                LocalDate.of(2024, 1, 1), null, "Class X (Metros)");
    }

    private PayrollHraRate entityFrom(Long id, PayrollHraRateRequest request) {
        PayrollHraRate rate = new PayrollHraRate(request.cityClass(), request.ratePercentage(), request.minAmount(),
                request.effectiveFrom(), request.effectiveTo(), request.remarks());
        ReflectionTestUtils.setField(rate, "id", id);
        return rate;
    }

    @Test
    void create_savesAndReturnsResponse() {
        PayrollHraRateRequest request = validRequest();
        when(payrollHraRateRepository.save(any(PayrollHraRate.class))).thenReturn(entityFrom(1L, request));

        PayrollHraRateResponse response = payrollHraRateService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.cityClass()).isEqualTo("X");
        assertThat(response.ratePercentage()).isEqualByComparingTo("30.00");
    }

    @Test
    void update_whenMissing_throwsMasterDataNotFoundException() {
        when(payrollHraRateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollHraRateService.update(99L, validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void update_settingEffectiveTo_closesOutTheSlab() {
        PayrollHraRate existing = entityFrom(1L, validRequest());
        when(payrollHraRateRepository.findById(1L)).thenReturn(Optional.of(existing));

        PayrollHraRateRequest closeOut = new PayrollHraRateRequest("X", new BigDecimal("30.00"), new BigDecimal("6750.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), "Superseded");
        PayrollHraRateResponse response = payrollHraRateService.update(1L, closeOut);

        assertThat(response.effectiveTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(response.remarks()).isEqualTo("Superseded");
    }

    @Test
    void listAll_mapsEveryRow() {
        when(payrollHraRateRepository.findAllByOrderByCityClassAscEffectiveFromDesc())
                .thenReturn(List.of(entityFrom(1L, validRequest())));

        List<PayrollHraRateResponse> result = payrollHraRateService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cityClass()).isEqualTo("X");
    }
}
