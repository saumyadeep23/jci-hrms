package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DistrictMasterRequest;
import in.gov.jci.hrms.dto.DistrictMasterResponse;
import in.gov.jci.hrms.entity.DistrictMaster;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
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
public class DistrictMasterService {

    private static final String ENTITY_NAME = "District";

    private final DistrictMasterRepository districtMasterRepository;
    private final StateMasterRepository stateMasterRepository;

    public DistrictMasterService(DistrictMasterRepository districtMasterRepository, StateMasterRepository stateMasterRepository) {
        this.districtMasterRepository = districtMasterRepository;
        this.stateMasterRepository = stateMasterRepository;
    }

    @Transactional
    public DistrictMasterResponse create(DistrictMasterRequest request) {
        DistrictMaster district = new DistrictMaster(request.districtCode(), request.districtName(),
                resolveState(request.stateId()), request.active());
        return DistrictMasterResponse.from(save(district));
    }

    public DistrictMasterResponse getById(UUID id) {
        return DistrictMasterResponse.from(findOrThrow(id));
    }

    public Page<DistrictMasterResponse> list(UUID stateId, Pageable pageable) {
        Page<DistrictMaster> page = stateId != null
                ? districtMasterRepository.findByStateId(stateId, pageable)
                : districtMasterRepository.findAll(pageable);
        return page.map(DistrictMasterResponse::from);
    }

    @Transactional
    public DistrictMasterResponse update(UUID id, DistrictMasterRequest request) {
        DistrictMaster district = findOrThrow(id);
        district.setDistrictCode(request.districtCode());
        district.setDistrictName(request.districtName());
        district.setState(resolveState(request.stateId()));
        district.setActive(request.active());
        return DistrictMasterResponse.from(save(district));
    }

    /** No deleted_at column on this table - "delete" just deactivates. Nothing else FKs to district_master. */
    @Transactional
    public void delete(UUID id) {
        DistrictMaster district = findOrThrow(id);
        district.setActive(false);
    }

    @Transactional
    public DistrictMasterResponse updateStatus(UUID id, boolean active) {
        DistrictMaster district = findOrThrow(id);
        district.setActive(active);
        return DistrictMasterResponse.from(district);
    }

    /** Nothing else FKs to district_master, so it never blocks deactivation. */
    public DependencyCheckResponse dependencies(UUID id) {
        findOrThrow(id);
        return new DependencyCheckResponse(false, List.of());
    }

    private StateMaster resolveState(UUID stateId) {
        return stateMasterRepository.findById(stateId)
                .orElseThrow(() -> new MasterDataNotFoundException("State", stateId));
    }

    private DistrictMaster findOrThrow(UUID id) {
        return districtMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private DistrictMaster save(DistrictMaster district) {
        try {
            return districtMasterRepository.saveAndFlush(district);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + district.getDistrictCode());
        }
    }
}
