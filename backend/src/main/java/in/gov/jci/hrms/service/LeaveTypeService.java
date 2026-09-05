package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveTypeMasterResponse;
import in.gov.jci.hrms.dto.LeaveTypeMasterUpdateRequest;
import in.gov.jci.hrms.dto.LeaveTypeRequest;
import in.gov.jci.hrms.dto.LeaveTypeResponse;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LeaveTypeService {

    private static final String ENTITY_NAME = "Leave Type";

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, LeaveBalanceRepository leaveBalanceRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @Transactional
    public LeaveTypeResponse create(LeaveTypeRequest request) {
        LeaveType leaveType = new LeaveType(request.code(), request.name(), request.annualQuota(),
                request.isEncashable(), request.active());
        leaveType.setMaxAccumulationDays(request.maxAccumulationDays());
        leaveType.setCareerLimitDays(request.careerLimitDays());
        return LeaveTypeResponse.from(save(leaveType));
    }

    public LeaveTypeResponse getById(Long id) {
        return LeaveTypeResponse.from(findOrThrow(id));
    }

    public Page<LeaveTypeResponse> list(Pageable pageable) {
        return leaveTypeRepository.findAll(pageable).map(LeaveTypeResponse::from);
    }

    @Transactional
    public LeaveTypeResponse update(Long id, LeaveTypeRequest request) {
        LeaveType leaveType = findOrThrow(id);
        leaveType.setCode(request.code());
        leaveType.setName(request.name());
        leaveType.setAnnualQuota(request.annualQuota());
        leaveType.setMaxAccumulationDays(request.maxAccumulationDays());
        leaveType.setEncashable(request.isEncashable());
        leaveType.setCareerLimitDays(request.careerLimitDays());
        leaveType.setActive(request.active());
        return LeaveTypeResponse.from(save(leaveType));
    }

    @Transactional
    public void delete(Long id) {
        LeaveType leaveType = findOrThrow(id);
        if (leaveBalanceRepository.existsByLeaveTypeId(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        leaveType.setDeletedAt(Instant.now());
    }

    /** Leave Type Master's list view - unpaged (this dataset is a handful of statutory leave types, not something that grows), and deliberately includes inactive types since an admin managing eligibility needs to see everything. */
    public List<LeaveTypeMasterResponse> listForMaster() {
        return leaveTypeRepository.findAll(Sort.by("code")).stream().map(LeaveTypeMasterResponse::from).toList();
    }

    @Transactional
    public LeaveTypeMasterResponse updateMaster(Long id, LeaveTypeMasterUpdateRequest request) {
        LeaveType leaveType = findOrThrow(id);
        leaveType.setMaxAccumulationDays(request.maxAccumulationCap());
        leaveType.setEncashable(request.isEncashable());
        leaveType.setEligibleCategories(request.eligibleCategories());
        return LeaveTypeMasterResponse.from(save(leaveType));
    }

    private LeaveType findOrThrow(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private LeaveType save(LeaveType leaveType) {
        try {
            return leaveTypeRepository.saveAndFlush(leaveType);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + leaveType.getCode());
        }
    }
}
