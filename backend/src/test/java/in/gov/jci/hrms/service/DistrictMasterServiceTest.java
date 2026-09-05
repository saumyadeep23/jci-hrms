package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DistrictMasterRequest;
import in.gov.jci.hrms.dto.DistrictMasterResponse;
import in.gov.jci.hrms.entity.DistrictMaster;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
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
class DistrictMasterServiceTest {

    private static final UUID STATE_ID = UUID.randomUUID();

    @Mock
    private DistrictMasterRepository districtMasterRepository;
    @Mock
    private StateMasterRepository stateMasterRepository;

    private DistrictMasterService districtMasterService;
    private StateMaster state;

    @BeforeEach
    void setUp() {
        districtMasterService = new DistrictMasterService(districtMasterRepository, stateMasterRepository);
        state = new StateMaster("WB", "West Bengal", StateType.STATE, true);
        ReflectionTestUtils.setField(state, "id", STATE_ID);
    }

    private DistrictMasterRequest validRequest() {
        return new DistrictMasterRequest("WB-KOL", "Kolkata", STATE_ID, true);
    }

    private DistrictMaster entityFrom(UUID id, DistrictMasterRequest request) {
        DistrictMaster district = new DistrictMaster(request.districtCode(), request.districtName(), state, request.active());
        ReflectionTestUtils.setField(district, "id", id);
        return district;
    }

    @Test
    void create_whenStateExists_savesAndReturnsResponse() {
        UUID id = UUID.randomUUID();
        when(stateMasterRepository.findById(STATE_ID)).thenReturn(Optional.of(state));
        when(districtMasterRepository.saveAndFlush(any(DistrictMaster.class))).thenReturn(entityFrom(id, validRequest()));

        DistrictMasterResponse response = districtMasterService.create(validRequest());

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.districtName()).isEqualTo("Kolkata");
        assertThat(response.stateId()).isEqualTo(STATE_ID);
        assertThat(response.stateName()).isEqualTo("West Bengal");
    }

    @Test
    void create_whenStateMissing_throwsMasterDataNotFoundException() {
        when(stateMasterRepository.findById(STATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> districtMasterService.create(validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("State");
    }

    @Test
    void create_whenCodeAlreadyTaken_throwsMasterDataConflictException() {
        when(stateMasterRepository.findById(STATE_ID)).thenReturn(Optional.of(state));
        when(districtMasterRepository.saveAndFlush(any(DistrictMaster.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> districtMasterService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        UUID missingId = UUID.randomUUID();
        when(districtMasterRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> districtMasterService.getById(missingId))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("District");
    }

    @Test
    void delete_softDeletes() {
        UUID id = UUID.randomUUID();
        DistrictMaster district = entityFrom(id, validRequest());
        when(districtMasterRepository.findById(id)).thenReturn(Optional.of(district));

        districtMasterService.delete(id);

        assertThat(district.isActive()).isFalse();
    }
}
