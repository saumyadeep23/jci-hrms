package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.ShiftMasterRequest;
import in.gov.jci.hrms.dto.ShiftMasterResponse;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.ShiftMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftMasterServiceTest {

    @Mock
    private ShiftMasterRepository shiftMasterRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private ShiftMasterService shiftMasterService;

    @BeforeEach
    void setUp() {
        shiftMasterService = new ShiftMasterService(shiftMasterRepository, new MasterDependencyService(jdbcTemplate));
    }

    private ShiftMasterRequest validRequest() {
        return new ShiftMasterRequest("SHIFT_A", "Shift A (Morning)", LocalTime.of(6, 0), LocalTime.of(14, 0), 10, false,
                480, 240, null, true);
    }

    private ShiftMaster entityFrom(Long id, ShiftMasterRequest request) {
        ShiftMaster shift = new ShiftMaster(request.shiftCode(), request.shiftName(), request.startTime(),
                request.endTime(), request.gracePeriodMinutes(), request.crossesMidnight(), request.active());
        ReflectionTestUtils.setField(shift, "id", id);
        return shift;
    }

    @Test
    void create_savesAndReturnsResponse() {
        ShiftMasterRequest request = validRequest();
        when(shiftMasterRepository.saveAndFlush(any(ShiftMaster.class))).thenReturn(entityFrom(1L, request));

        ShiftMasterResponse response = shiftMasterService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.shiftCode()).isEqualTo("SHIFT_A");
        assertThat(response.crossesMidnight()).isFalse();
    }

    @Test
    void create_whenOvernightShift_allowsEndTimeBeforeStartTime() {
        ShiftMasterRequest overnight = new ShiftMasterRequest("SHIFT_C", "Shift C (Night)", LocalTime.of(22, 0), LocalTime.of(6, 0), 10, true,
                480, 240, null, true);
        when(shiftMasterRepository.saveAndFlush(any(ShiftMaster.class))).thenReturn(entityFrom(2L, overnight));

        ShiftMasterResponse response = shiftMasterService.create(overnight);

        assertThat(response.crossesMidnight()).isTrue();
        assertThat(response.endTime()).isBefore(response.startTime());
    }

    @Test
    void create_whenNotOvernightAndEndTimeNotAfterStartTime_throwsMasterDataValidationException() {
        ShiftMasterRequest invalid = new ShiftMasterRequest("BAD", "Bad Shift", LocalTime.of(14, 0), LocalTime.of(6, 0), 10, false,
                480, 240, null, true);

        assertThatThrownBy(() -> shiftMasterService.create(invalid))
                .isInstanceOf(MasterDataValidationException.class);

        verify(shiftMasterRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenShiftCodeAlreadyInUse_throwsMasterDataConflictException() {
        when(shiftMasterRepository.saveAndFlush(any(ShiftMaster.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> shiftMasterService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void delete_softDeletesAndDeactivates_sinceNothingReferencesShiftsYet() {
        ShiftMaster shift = entityFrom(3L, validRequest());
        when(shiftMasterRepository.findById(3L)).thenReturn(Optional.of(shift));

        shiftMasterService.delete(3L);

        assertThat(shift.getDeletedAt()).isNotNull();
        assertThat(shift.isActive()).isFalse();
    }
}
