package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DaRateHistoryRequest;
import in.gov.jci.hrms.dto.DaRateHistoryResponse;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.DaRateNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
class DaRateHistoryServiceTest {

    @Mock
    private DaRateHistoryRepository daRateHistoryRepository;

    private DaRateHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DaRateHistoryService(daRateHistoryRepository);
    }

    private DaRateHistoryRequest requestFor(LocalDate effectiveFrom) {
        return new DaRateHistoryRequest(ScaleType.IDA, effectiveFrom, new BigDecimal("18.00"), true,
                "F.No.1(3)/2026-E.II", LocalDate.of(2026, 3, 20), "Revised per DoPT circular");
    }

    private DaRateHistory entityFrom(Long id, DaRateHistoryRequest request, LocalDate effectiveTo) {
        DaRateHistory rate = new DaRateHistory(request.scaleType(), request.effectiveFrom(), effectiveTo,
                request.daPercentage(), request.active(), request.orderNumber(), request.orderDate(), request.remarks());
        ReflectionTestUtils.setField(rate, "id", id);
        return rate;
    }

    @Test
    void create_whenNoNeighbors_appendsWithOpenEndedRange() {
        DaRateHistoryRequest request = requestFor(LocalDate.of(2026, 4, 1));
        when(daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(ScaleType.IDA, request.effectiveFrom())).thenReturn(false);
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(daRateHistoryRepository.saveAndFlush(any(DaRateHistory.class))).thenAnswer(inv -> {
            DaRateHistory saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        DaRateHistoryResponse response = service.create(request);

        assertThat(response.effectiveTo()).isNull();
        assertThat(response.orderNumber()).isEqualTo("F.No.1(3)/2026-E.II");
    }

    @Test
    void create_closesPredecessorsOpenEndedRange() {
        DaRateHistoryRequest request = requestFor(LocalDate.of(2026, 4, 1));
        DaRateHistory predecessor = entityFrom(1L, requestFor(LocalDate.of(2025, 10, 1)), null);

        when(daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(ScaleType.IDA, request.effectiveFrom())).thenReturn(false);
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(ScaleType.IDA, request.effectiveFrom()))
                .thenReturn(Optional.of(predecessor));
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(daRateHistoryRepository.saveAndFlush(any(DaRateHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        DaRateHistoryResponse response = service.create(request);

        assertThat(predecessor.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(response.effectiveTo()).isNull(); // the new row is now the open-ended, current one
    }

    @Test
    void create_insertedMidHistory_boundsNewRowAgainstSuccessorAndClosesPredecessor() {
        DaRateHistoryRequest request = requestFor(LocalDate.of(2026, 1, 1));
        DaRateHistory predecessor = entityFrom(1L, requestFor(LocalDate.of(2025, 7, 1)), null);
        DaRateHistory successor = entityFrom(2L, requestFor(LocalDate.of(2026, 4, 1)), null);

        when(daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(ScaleType.IDA, request.effectiveFrom())).thenReturn(false);
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(ScaleType.IDA, request.effectiveFrom()))
                .thenReturn(Optional.of(predecessor));
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(ScaleType.IDA, request.effectiveFrom()))
                .thenReturn(Optional.of(successor));
        when(daRateHistoryRepository.saveAndFlush(any(DaRateHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        DaRateHistoryResponse response = service.create(request);

        assertThat(predecessor.getEffectiveTo()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(response.effectiveTo()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void create_whenDuplicateEffectiveFromForSameScaleType_throwsMasterDataConflictException() {
        DaRateHistoryRequest request = requestFor(LocalDate.of(2026, 4, 1));
        when(daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(ScaleType.IDA, request.effectiveFrom())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void create_whenSaveRacesIntoUniqueIndex_throwsMasterDataConflictException() {
        DaRateHistoryRequest request = requestFor(LocalDate.of(2026, 4, 1));
        when(daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(ScaleType.IDA, request.effectiveFrom())).thenReturn(false);
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(daRateHistoryRepository.findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(daRateHistoryRepository.saveAndFlush(any(DaRateHistory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void list_returnsRepositoryOrderingMappedToResponses() {
        DaRateHistory ida = entityFrom(1L, requestFor(LocalDate.of(2026, 4, 1)), null);
        DaRateHistory cda = entityFrom(2L, new DaRateHistoryRequest(ScaleType.CDA, LocalDate.of(2026, 1, 1),
                new BigDecimal("50.00"), true, null, null, null), null);
        when(daRateHistoryRepository.findAllByOrderByScaleTypeAscEffectiveFromDesc()).thenReturn(List.of(cda, ida));

        List<DaRateHistoryResponse> result = service.list();

        assertThat(result).extracting(DaRateHistoryResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void getCurrent_whenFound_returnsResponse() {
        DaRateHistory rate = entityFrom(1L, requestFor(LocalDate.of(2026, 4, 1)), null);
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                ScaleType.IDA, LocalDate.of(2026, 6, 15))).thenReturn(Optional.of(rate));

        DaRateHistoryResponse response = service.getCurrent(ScaleType.IDA, LocalDate.of(2026, 6, 15));

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getCurrent_whenNoneFound_throwsDaRateNotFoundException() {
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(ScaleType.IDA, LocalDate.of(2020, 1, 1)))
                .isInstanceOf(DaRateNotFoundException.class);
    }
}
