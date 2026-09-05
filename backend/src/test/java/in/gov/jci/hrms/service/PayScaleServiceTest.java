package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayScaleRequest;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.PayScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayScaleServiceTest {

    @Mock
    private PayScaleRepository payScaleRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private PayScaleService payScaleService;

    @BeforeEach
    void setUp() {
        payScaleService = new PayScaleService(payScaleRepository, new MasterDependencyService(jdbcTemplate));
    }

    private PayScaleRequest validRequest() {
        return new PayScaleRequest(ScaleType.IDA, "E1", new BigDecimal("40000.00"), new BigDecimal("60000.00"),
                new BigDecimal("3.00"), true);
    }

    private PayScale entityFrom(Long id, PayScaleRequest request) {
        PayScale payScale = new PayScale(request.scaleType(), request.grade(), request.minimumBasic(),
                request.maximumBasic(), request.incrementRate(), request.active());
        ReflectionTestUtils.setField(payScale, "id", id);
        return payScale;
    }

    @Test
    void create_savesAndReturnsResponse() {
        PayScaleRequest request = validRequest();
        when(payScaleRepository.saveAndFlush(any(PayScale.class))).thenReturn(entityFrom(1L, request));

        PayScaleResponse response = payScaleService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.scaleType()).isEqualTo(ScaleType.IDA);
        assertThat(response.grade()).isEqualTo("E1");
    }

    @Test
    void create_whenMinimumGreaterThanMaximum_throwsMasterDataValidationException() {
        PayScaleRequest invalid = new PayScaleRequest(ScaleType.IDA, "E1", new BigDecimal("70000.00"),
                new BigDecimal("60000.00"), new BigDecimal("3.00"), true);

        assertThatThrownBy(() -> payScaleService.create(invalid))
                .isInstanceOf(MasterDataValidationException.class);

        verify(payScaleRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenScaleTypeAndGradeAlreadyInUse_throwsMasterDataConflictException() {
        when(payScaleRepository.saveAndFlush(any(PayScale.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> payScaleService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void delete_whenReferencedByActiveEmployee_throwsMasterDataInUseException() {
        PayScale payScale = entityFrom(2L, validRequest());
        when(payScaleRepository.findById(2L)).thenReturn(java.util.Optional.of(payScale));
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> payScaleService.delete(2L))
                .isInstanceOf(MasterDataInUseException.class);
    }

    @Test
    void delete_whenNotReferenced_softDeletesAndDeactivates() {
        PayScale payScale = entityFrom(3L, validRequest());
        when(payScaleRepository.findById(3L)).thenReturn(java.util.Optional.of(payScale));
        when(jdbcTemplate.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class), any(Object.class)))
                .thenReturn(0L);

        payScaleService.delete(3L);

        assertThat(payScale.getDeletedAt()).isNotNull();
        assertThat(payScale.isActive()).isFalse();
    }
}
