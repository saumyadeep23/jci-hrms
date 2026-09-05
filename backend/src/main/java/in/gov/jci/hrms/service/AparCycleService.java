package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AparCycleRequest;
import in.gov.jci.hrms.dto.AparCycleResponse;
import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.AparCycleStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.AparCycleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Minimal cycle-level administration (create + linearly advance status) -
 * added because EmployeeApar needs a real, persisted cycle to reference.
 * A cycle's status is a broad organizational phase marker; it is not
 * cross-validated against any individual EmployeeApar's own status.
 */
@Service
@Transactional(readOnly = true)
public class AparCycleService {

    private static final String ENTITY_NAME = "APAR Cycle";
    private static final List<AparCycleStatus> ORDER = List.of(
            AparCycleStatus.INITIATED, AparCycleStatus.SELF_APPRAISAL, AparCycleStatus.REPORTING,
            AparCycleStatus.REVIEWING, AparCycleStatus.ACCEPTING, AparCycleStatus.DISCLOSED, AparCycleStatus.CLOSED);

    private final AparCycleRepository aparCycleRepository;

    public AparCycleService(AparCycleRepository aparCycleRepository) {
        this.aparCycleRepository = aparCycleRepository;
    }

    @Transactional
    public AparCycleResponse create(AparCycleRequest request) {
        AparCycle cycle = new AparCycle(request.cycleYear(), request.startDate(), request.endDate());
        try {
            return AparCycleResponse.from(aparCycleRepository.saveAndFlush(cycle));
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " already exists for " + request.cycleYear());
        }
    }

    public AparCycleResponse getById(Long id) {
        return AparCycleResponse.from(findOrThrow(id));
    }

    public Page<AparCycleResponse> list(Pageable pageable) {
        return aparCycleRepository.findAll(pageable).map(AparCycleResponse::from);
    }

    @Transactional
    public AparCycleResponse advance(Long id) {
        AparCycle cycle = findOrThrow(id);
        int currentIndex = ORDER.indexOf(cycle.getStatus());
        if (currentIndex == ORDER.size() - 1) {
            throw new BusinessRuleViolationException(ENTITY_NAME + " " + id + " is already " + cycle.getStatus());
        }
        cycle.setStatus(ORDER.get(currentIndex + 1));
        return AparCycleResponse.from(cycle);
    }

    AparCycle findOrThrow(Long id) {
        return aparCycleRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
