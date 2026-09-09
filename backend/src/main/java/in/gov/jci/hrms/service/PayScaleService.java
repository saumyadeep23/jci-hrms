package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.PayScaleRequest;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.entity.PayScale;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.PayScaleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deprecated (Phase 1 of the pay_scale_master -> grade_scale_master
 * cutover, V60): pay_scale_master itself is untouched and this service's
 * read/write logic still works, but PayScaleController now only exposes
 * its GET endpoints - see that controller's own javadoc. Kept, not
 * deleted, since dropping the table is a separate, later Phase 2 pending
 * confirmation nothing still needs pay_scale_master's extra
 * per-designation/historical rows that grade_scale_master doesn't carry.
 */
@Deprecated
@Service
@Transactional(readOnly = true)
public class PayScaleService {

    private static final String ENTITY_NAME = "Pay Scale";

    /**
     * Empty (V60): the two columns this used to probe - employees.pay_scale_id
     * and employee_employment_categories.pay_scale_id - were both dropped by
     * that migration (the latter's FK had drifted to point at
     * grade_scale_master anyway, not pay_scale_master, before it was
     * dropped). Nothing references pay_scale_master by FK anymore, so
     * there's nothing left to probe - see MasterDependencyService.check(),
     * which is a no-op over an empty probe list.
     */
    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of();

    private final PayScaleRepository payScaleRepository;
    private final MasterDependencyService masterDependencyService;

    public PayScaleService(PayScaleRepository payScaleRepository, MasterDependencyService masterDependencyService) {
        this.payScaleRepository = payScaleRepository;
        this.masterDependencyService = masterDependencyService;
    }

    @Transactional
    public PayScaleResponse create(PayScaleRequest request) {
        validateRange(request);
        PayScale payScale = new PayScale(
                request.scaleType(), request.grade(), request.minimumBasic(),
                request.maximumBasic(), request.incrementRate(), request.active());
        return PayScaleResponse.from(save(payScale));
    }

    public PayScaleResponse getById(Long id) {
        return PayScaleResponse.from(findOrThrow(id));
    }

    public Page<PayScaleResponse> list(Pageable pageable) {
        return payScaleRepository.findAll(pageable).map(PayScaleResponse::from);
    }

    @Transactional
    public PayScaleResponse update(Long id, PayScaleRequest request) {
        validateRange(request);
        PayScale payScale = findOrThrow(id);
        payScale.setScaleType(request.scaleType());
        payScale.setGrade(request.grade());
        payScale.setMinimumBasic(request.minimumBasic());
        payScale.setMaximumBasic(request.maximumBasic());
        payScale.setIncrementRate(request.incrementRate());
        payScale.setActive(request.active());
        return PayScaleResponse.from(save(payScale));
    }

    @Transactional
    public PayScaleResponse updateStatus(Long id, boolean active) {
        PayScale payScale = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            payScale.setActive(false);
            payScale.setDeletedAt(Instant.now());
        } else {
            payScale.setActive(true);
            payScale.setDeletedAt(null);
        }
        return PayScaleResponse.from(payScale);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    @Transactional
    public void delete(Long id) {
        updateStatus(id, false);
    }

    private void validateRange(PayScaleRequest request) {
        if (request.minimumBasic().compareTo(request.maximumBasic()) > 0) {
            throw new MasterDataValidationException("minimumBasic must not be greater than maximumBasic");
        }
    }

    private PayScale findOrThrow(Long id) {
        return payScaleRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private PayScale save(PayScale payScale) {
        try {
            return payScaleRepository.saveAndFlush(payScale);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " already exists for " + payScale.getScaleType() + " grade " + payScale.getGrade());
        }
    }
}
