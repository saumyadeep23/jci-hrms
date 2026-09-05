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

@Service
@Transactional(readOnly = true)
public class PayScaleService {

    private static final String ENTITY_NAME = "Pay Scale";

    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employees", "pay_scale_id", "Employees", true),
            new MasterDependencyService.DependencyProbe("employee_employment_categories", "pay_scale_id", "Employment Categories", true)
    );

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
