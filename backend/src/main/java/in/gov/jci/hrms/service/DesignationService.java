package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.DesignationRequest;
import in.gov.jci.hrms.dto.DesignationResponse;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DesignationService {

    private static final String ENTITY_NAME = "Designation";

    private static final List<MasterDependencyService.DependencyProbe> DEPENDENCY_PROBES = List.of(
            new MasterDependencyService.DependencyProbe("employees", "designation_id", "Employees", true),
            new MasterDependencyService.DependencyProbe("post_master", "designation_id", "Sanctioned Posts", true),
            new MasterDependencyService.DependencyProbe("tada_rate_master", "designation_id", "TA/DA Rates", true)
    );

    private final DesignationRepository designationRepository;
    private final MasterDependencyService masterDependencyService;
    private final GradeScaleMasterRepository gradeScaleMasterRepository;

    public DesignationService(DesignationRepository designationRepository, MasterDependencyService masterDependencyService,
                               GradeScaleMasterRepository gradeScaleMasterRepository) {
        this.designationRepository = designationRepository;
        this.masterDependencyService = masterDependencyService;
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
    }

    @Transactional
    public DesignationResponse create(DesignationRequest request) {
        Designation designation = new Designation(request.title());
        designation.setDescription(request.description());
        designation.setGradeScale(resolveGradeScale(request.gradeScaleId()));
        return DesignationResponse.from(save(designation));
    }

    private GradeScaleMaster resolveGradeScale(Long id) {
        if (id == null) return null;
        return gradeScaleMasterRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", id));
    }

    public DesignationResponse getById(Long id) {
        return DesignationResponse.from(findOrThrow(id));
    }

    public Page<DesignationResponse> list(Pageable pageable) {
        return designationRepository.findAll(pageable).map(DesignationResponse::from);
    }

    @Transactional
    public DesignationResponse update(Long id, DesignationRequest request) {
        Designation designation = findOrThrow(id);
        designation.setTitle(request.title());
        designation.setDescription(request.description());
        designation.setGradeScale(resolveGradeScale(request.gradeScaleId()));
        return DesignationResponse.from(save(designation));
    }

    @Transactional
    public DesignationResponse updateStatus(Long id, boolean active) {
        Designation designation = findOrThrow(id);
        if (!active) {
            DependencyCheckResponse dependencies = masterDependencyService.check(DEPENDENCY_PROBES, id);
            if (dependencies.hasActiveDependencies()) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            designation.setDeletedAt(Instant.now());
        } else {
            designation.setDeletedAt(null);
        }
        return DesignationResponse.from(designation);
    }

    public DependencyCheckResponse dependencies(Long id) {
        findOrThrow(id);
        return masterDependencyService.check(DEPENDENCY_PROBES, id);
    }

    @Transactional
    public void delete(Long id) {
        updateStatus(id, false);
    }

    private Designation findOrThrow(Long id) {
        return designationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private Designation save(Designation designation) {
        try {
            return designationRepository.saveAndFlush(designation);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " title already in use: " + designation.getTitle());
        }
    }
}
