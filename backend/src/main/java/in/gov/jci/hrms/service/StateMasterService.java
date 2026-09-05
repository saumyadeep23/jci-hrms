package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DependencyUsage;
import in.gov.jci.hrms.dto.StateMasterRequest;
import in.gov.jci.hrms.dto.StateMasterResponse;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DistrictMasterRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StateMasterService {

    private static final String ENTITY_NAME = "State";

    private final StateMasterRepository stateMasterRepository;
    private final DistrictMasterRepository districtMasterRepository;

    public StateMasterService(StateMasterRepository stateMasterRepository, DistrictMasterRepository districtMasterRepository) {
        this.stateMasterRepository = stateMasterRepository;
        this.districtMasterRepository = districtMasterRepository;
    }

    @Transactional
    public StateMasterResponse create(StateMasterRequest request) {
        StateMaster state = new StateMaster(request.stateCode(), request.stateName(), request.stateType(), request.active());
        return StateMasterResponse.from(save(state));
    }

    public StateMasterResponse getById(UUID id) {
        return StateMasterResponse.from(findOrThrow(id));
    }

    public Page<StateMasterResponse> list(Pageable pageable) {
        return stateMasterRepository.findAll(pageable).map(StateMasterResponse::from);
    }

    @Transactional
    public StateMasterResponse update(UUID id, StateMasterRequest request) {
        StateMaster state = findOrThrow(id);
        state.setStateCode(request.stateCode());
        state.setStateName(request.stateName());
        state.setStateType(request.stateType());
        state.setActive(request.active());
        return StateMasterResponse.from(save(state));
    }

    /** No deleted_at column on this table (unlike most master entities here) - "delete" just deactivates. */
    @Transactional
    public void delete(UUID id) {
        StateMaster state = findOrThrow(id);
        if (districtMasterRepository.existsByStateId(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        state.setActive(false);
    }

    @Transactional
    public StateMasterResponse updateStatus(UUID id, boolean active) {
        StateMaster state = findOrThrow(id);
        if (!active && districtMasterRepository.existsByStateId(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        state.setActive(active);
        return StateMasterResponse.from(state);
    }

    public DependencyCheckResponse dependencies(UUID id) {
        findOrThrow(id);
        boolean hasDistricts = districtMasterRepository.existsByStateId(id);
        List<DependencyUsage> usages = hasDistricts
                ? List.of(new DependencyUsage("district_master", "Districts", 1L))
                : List.of();
        return new DependencyCheckResponse(hasDistricts, usages);
    }

    private StateMaster findOrThrow(UUID id) {
        return stateMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private StateMaster save(StateMaster state) {
        try {
            return stateMasterRepository.saveAndFlush(state);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + state.getStateCode());
        }
    }
}
