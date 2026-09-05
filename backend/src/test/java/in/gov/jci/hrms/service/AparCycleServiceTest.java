package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AparCycleRequest;
import in.gov.jci.hrms.dto.AparCycleResponse;
import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.AparCycleStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.AparCycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AparCycleServiceTest {

    private static final Long CYCLE_ID = 1L;

    @Mock
    private AparCycleRepository aparCycleRepository;

    private AparCycleService aparCycleService;

    @BeforeEach
    void setUp() {
        aparCycleService = new AparCycleService(aparCycleRepository);
    }

    private AparCycle cycleWith(AparCycleStatus status) {
        AparCycle cycle = new AparCycle("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
        cycle.setStatus(status);
        return cycle;
    }

    @Test
    void create_savesInInitiatedStatus() {
        when(aparCycleRepository.saveAndFlush(any(AparCycle.class))).thenAnswer(inv -> {
            AparCycle saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", CYCLE_ID);
            return saved;
        });

        AparCycleResponse response = aparCycleService.create(
                new AparCycleRequest("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31)));

        assertThat(response.status()).isEqualTo(AparCycleStatus.INITIATED);
    }

    @Test
    void create_whenCycleYearAlreadyExists_throwsMasterDataConflictException() {
        when(aparCycleRepository.saveAndFlush(any(AparCycle.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> aparCycleService.create(
                new AparCycleRequest("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31))))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void advance_walksThroughEveryStatusInOrder() {
        AparCycle cycle = cycleWith(AparCycleStatus.INITIATED);
        when(aparCycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        AparCycleStatus[] expectedOrder = {
                AparCycleStatus.SELF_APPRAISAL, AparCycleStatus.REPORTING, AparCycleStatus.REVIEWING,
                AparCycleStatus.ACCEPTING, AparCycleStatus.DISCLOSED, AparCycleStatus.CLOSED
        };

        for (AparCycleStatus expected : expectedOrder) {
            AparCycleResponse response = aparCycleService.advance(CYCLE_ID);
            assertThat(response.status()).isEqualTo(expected);
        }
    }

    @Test
    void advance_whenAlreadyClosed_throwsBusinessRuleViolationException() {
        AparCycle cycle = cycleWith(AparCycleStatus.CLOSED);
        when(aparCycleRepository.findById(CYCLE_ID)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> aparCycleService.advance(CYCLE_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(aparCycleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aparCycleService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
