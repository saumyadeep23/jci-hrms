package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.ShiftMasterRequest;
import in.gov.jci.hrms.dto.ShiftMasterResponse;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.ShiftMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * employee_shift_schedule (V44) is the only thing that references
 * shift_master today - it has no soft-delete column of its own (a roster
 * row is either there or it isn't), hence hasSoftDelete=false.
 */
@Service
@Transactional(readOnly = true)
public class ShiftMasterService {

    private static final String ENTITY_NAME = "Shift";

    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employee_shift_schedule", "shift_id", "Roster Assignments", false)
    );

    private final ShiftMasterRepository shiftMasterRepository;
    private final MasterDependencyService masterDependencyService;

    public ShiftMasterService(ShiftMasterRepository shiftMasterRepository, MasterDependencyService masterDependencyService) {
        this.shiftMasterRepository = shiftMasterRepository;
        this.masterDependencyService = masterDependencyService;
    }

    @Transactional
    public ShiftMasterResponse create(ShiftMasterRequest request) {
        validateTimes(request);
        ShiftMaster shift = new ShiftMaster(request.shiftCode(), request.shiftName(), request.startTime(),
                request.endTime(), request.gracePeriodMinutes(), request.crossesMidnight(), request.active());
        shift.setFullDayMinutes(request.fullDayMinutes());
        shift.setHalfDayMinutes(request.halfDayMinutes());
        shift.setApplicableOfficeType(request.applicableOfficeType());
        return ShiftMasterResponse.from(save(shift));
    }

    public ShiftMasterResponse getById(Long id) {
        return ShiftMasterResponse.from(findOrThrow(id));
    }

    public Page<ShiftMasterResponse> list(Pageable pageable) {
        return shiftMasterRepository.findAll(pageable).map(ShiftMasterResponse::from);
    }

    @Transactional
    public ShiftMasterResponse update(Long id, ShiftMasterRequest request) {
        validateTimes(request);
        ShiftMaster shift = findOrThrow(id);
        shift.setShiftCode(request.shiftCode());
        shift.setShiftName(request.shiftName());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setGracePeriodMinutes(request.gracePeriodMinutes());
        shift.setCrossesMidnight(request.crossesMidnight());
        shift.setFullDayMinutes(request.fullDayMinutes());
        shift.setHalfDayMinutes(request.halfDayMinutes());
        shift.setApplicableOfficeType(request.applicableOfficeType());
        shift.setActive(request.active());
        return ShiftMasterResponse.from(save(shift));
    }

    @Transactional
    public ShiftMasterResponse updateStatus(Long id, boolean active) {
        ShiftMaster shift = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            shift.setActive(false);
            shift.setDeletedAt(Instant.now());
        } else {
            shift.setActive(true);
            shift.setDeletedAt(null);
        }
        return ShiftMasterResponse.from(shift);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    @Transactional
    public void delete(Long id) {
        updateStatus(id, false);
    }

    /** A non-overnight shift's end must be after its start; an overnight (crossesMidnight) shift is expected to have endTime <= startTime. */
    private void validateTimes(ShiftMasterRequest request) {
        if (!request.crossesMidnight() && !request.endTime().isAfter(request.startTime())) {
            throw new MasterDataValidationException("endTime must be after startTime unless the shift crosses midnight");
        }
    }

    private ShiftMaster findOrThrow(Long id) {
        return shiftMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private ShiftMaster save(ShiftMaster shift) {
        try {
            return shiftMasterRepository.saveAndFlush(shift);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already exists: " + shift.getShiftCode());
        }
    }
}
