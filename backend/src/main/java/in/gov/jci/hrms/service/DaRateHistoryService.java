package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DaRateHistoryRequest;
import in.gov.jci.hrms.dto.DaRateHistoryResponse;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.DaRateNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * effectiveTo is always server-computed here, never client-supplied (see
 * DaRateHistoryRequest) - create() maintains the invariant that no two rows
 * of the same scaleType ever have overlapping [effectiveFrom, effectiveTo]
 * ranges by closing out whichever existing row's range the new one lands
 * inside, and re-bounding the new row against whatever comes after it. The
 * only overlap that can still occur is a literal duplicate effectiveFrom for
 * the same scaleType, which is rejected outright (also enforced by a unique
 * index at the DB level, in case of a race between the check and the
 * insert).
 */
@Service
@Transactional(readOnly = true)
public class DaRateHistoryService {

    private static final String ENTITY_NAME = "DA Rate";

    private final DaRateHistoryRepository daRateHistoryRepository;

    public DaRateHistoryService(DaRateHistoryRepository daRateHistoryRepository) {
        this.daRateHistoryRepository = daRateHistoryRepository;
    }

    @Transactional
    public DaRateHistoryResponse create(DaRateHistoryRequest request) {
        if (daRateHistoryRepository.existsByScaleTypeAndEffectiveFrom(request.scaleType(), request.effectiveFrom())) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " already has an entry for " + request.scaleType() + " effective " + request.effectiveFrom());
        }

        Optional<DaRateHistory> predecessor = daRateHistoryRepository
                .findTopByScaleTypeAndEffectiveFromLessThanOrderByEffectiveFromDesc(request.scaleType(), request.effectiveFrom());
        Optional<DaRateHistory> successor = daRateHistoryRepository
                .findTopByScaleTypeAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(request.scaleType(), request.effectiveFrom());

        predecessor.ifPresent(prior -> prior.setEffectiveTo(request.effectiveFrom().minusDays(1)));
        LocalDate newEffectiveTo = successor.map(next -> next.getEffectiveFrom().minusDays(1)).orElse(null);

        DaRateHistory rate = new DaRateHistory(request.scaleType(), request.effectiveFrom(), newEffectiveTo,
                request.daPercentage(), request.active(), request.orderNumber(), request.orderDate(), request.remarks());

        return DaRateHistoryResponse.from(save(rate));
    }

    public List<DaRateHistoryResponse> list() {
        return daRateHistoryRepository.findAllByOrderByScaleTypeAscEffectiveFromDesc().stream()
                .map(DaRateHistoryResponse::from)
                .toList();
    }

    public DaRateHistoryResponse getCurrent(ScaleType scaleType, LocalDate effectiveDate) {
        return daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, effectiveDate)
                .map(DaRateHistoryResponse::from)
                .orElseThrow(() -> new DaRateNotFoundException(scaleType, effectiveDate));
    }

    private DaRateHistory save(DaRateHistory rate) {
        try {
            return daRateHistoryRepository.saveAndFlush(rate);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " already has an entry for " + rate.getScaleType() + " effective " + rate.getEffectiveFrom());
        }
    }
}
