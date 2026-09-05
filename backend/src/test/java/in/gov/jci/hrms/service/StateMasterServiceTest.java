package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.StateMasterRequest;
import in.gov.jci.hrms.dto.StateMasterResponse;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DistrictMasterRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateMasterServiceTest {

    @Mock
    private StateMasterRepository stateMasterRepository;
    @Mock
    private DistrictMasterRepository districtMasterRepository;

    private StateMasterService stateMasterService;

    @BeforeEach
    void setUp() {
        stateMasterService = new StateMasterService(stateMasterRepository, districtMasterRepository);
    }

    private StateMasterRequest validRequest() {
        return new StateMasterRequest("WB", "West Bengal", StateType.STATE, true);
    }

    private StateMaster entityFrom(UUID id, StateMasterRequest request) {
        StateMaster state = new StateMaster(request.stateCode(), request.stateName(), request.stateType(), request.active());
        ReflectionTestUtils.setField(state, "id", id);
        return state;
    }

    @Test
    void create_savesAndReturnsResponse() {
        StateMasterRequest request = validRequest();
        UUID id = UUID.randomUUID();
        when(stateMasterRepository.saveAndFlush(any(StateMaster.class))).thenReturn(entityFrom(id, request));

        StateMasterResponse response = stateMasterService.create(request);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.stateName()).isEqualTo("West Bengal");
        assertThat(response.stateType()).isEqualTo(StateType.STATE);
    }

    @Test
    void create_whenCodeAlreadyTaken_throwsMasterDataConflictException() {
        when(stateMasterRepository.saveAndFlush(any(StateMaster.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> stateMasterService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        UUID missingId = UUID.randomUUID();
        when(stateMasterRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stateMasterService.getById(missingId))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("State");
    }

    @Test
    void update_appliesAllFields() {
        UUID id = UUID.randomUUID();
        StateMaster state = entityFrom(id, validRequest());
        when(stateMasterRepository.findById(id)).thenReturn(Optional.of(state));
        when(stateMasterRepository.saveAndFlush(any(StateMaster.class))).thenAnswer(inv -> inv.getArgument(0));

        StateMasterRequest updated = new StateMasterRequest("KA", "Karnataka", StateType.STATE, false);
        StateMasterResponse response = stateMasterService.update(id, updated);

        assertThat(response.stateCode()).isEqualTo("KA");
        assertThat(response.stateName()).isEqualTo("Karnataka");
        assertThat(response.active()).isFalse();
    }

    @Test
    void delete_whenNoDistrictsReference_softDeletes() {
        UUID id = UUID.randomUUID();
        StateMaster state = entityFrom(id, validRequest());
        when(stateMasterRepository.findById(id)).thenReturn(Optional.of(state));
        when(districtMasterRepository.existsByStateId(id)).thenReturn(false);

        stateMasterService.delete(id);

        assertThat(state.isActive()).isFalse();
    }

    @Test
    void delete_whenDistrictsStillReference_throwsMasterDataInUseException() {
        UUID id = UUID.randomUUID();
        StateMaster state = entityFrom(id, validRequest());
        when(stateMasterRepository.findById(id)).thenReturn(Optional.of(state));
        when(districtMasterRepository.existsByStateId(id)).thenReturn(true);

        assertThatThrownBy(() -> stateMasterService.delete(id))
                .isInstanceOf(MasterDataInUseException.class);

        assertThat(state.isActive()).isTrue();
    }
}
