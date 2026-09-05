package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.GradeScaleCreateRequest;
import in.gov.jci.hrms.dto.GradeScaleMasterResponse;
import in.gov.jci.hrms.dto.GradeScaleUpdateRequest;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GradeScaleMasterService {

    private final GradeScaleMasterRepository gradeScaleMasterRepository;

    public GradeScaleMasterService(GradeScaleMasterRepository gradeScaleMasterRepository) {
        this.gradeScaleMasterRepository = gradeScaleMasterRepository;
    }

    public List<GradeScaleMasterResponse> list() {
        return gradeScaleMasterRepository.findAllByOrderByHierarchyLevelAsc().stream()
                .map(GradeScaleMasterResponse::from)
                .toList();
    }

    public GradeScaleMasterResponse getById(Long id) {
        return GradeScaleMasterResponse.from(findOrThrow(id));
    }

    @Transactional
    public GradeScaleMasterResponse create(GradeScaleCreateRequest request) {
        GradeScaleMaster gradeScale = new GradeScaleMaster(request.scaleCode(), request.cadre(), request.hierarchyLevel(),
                request.boardLevel(), request.minimumBasic(), request.maximumBasic());
        applyOptionalFields(gradeScale, request.incrementRate(), request.effectiveDate(), request.contractualLumpsum(),
                request.outsourcedCtc(), request.active());
        return GradeScaleMasterResponse.from(save(gradeScale));
    }

    @Transactional
    public GradeScaleMasterResponse update(String scaleCode, GradeScaleUpdateRequest request) {
        GradeScaleMaster gradeScale = findByScaleCodeOrThrow(scaleCode);
        gradeScale.setCadre(request.cadre());
        gradeScale.setHierarchyLevel(request.hierarchyLevel());
        gradeScale.setBoardLevel(request.boardLevel());
        gradeScale.setMinimumBasic(request.minimumBasic());
        gradeScale.setMaximumBasic(request.maximumBasic());
        applyOptionalFields(gradeScale, request.incrementRate(), request.effectiveDate(), request.contractualLumpsum(),
                request.outsourcedCtc(), request.active());
        return GradeScaleMasterResponse.from(save(gradeScale));
    }

    private void applyOptionalFields(GradeScaleMaster gradeScale, BigDecimal incrementRate, LocalDate effectiveDate,
                                      BigDecimal contractualLumpsum, BigDecimal outsourcedCtc, boolean active) {
        if (incrementRate != null) {
            gradeScale.setIncrementRate(incrementRate);
        }
        if (effectiveDate != null) {
            gradeScale.setEffectiveDate(effectiveDate);
        }
        gradeScale.setContractualLumpsum(contractualLumpsum);
        gradeScale.setOutsourcedCtc(outsourcedCtc);
        gradeScale.setActive(active);
    }

    private GradeScaleMaster save(GradeScaleMaster gradeScale) {
        try {
            return gradeScaleMasterRepository.saveAndFlush(gradeScale);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    "Grade scale code or hierarchy level already in use: " + gradeScale.getScaleCode());
        }
    }

    private GradeScaleMaster findOrThrow(Long id) {
        return gradeScaleMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", id));
    }

    private GradeScaleMaster findByScaleCodeOrThrow(String scaleCode) {
        return gradeScaleMasterRepository.findByScaleCode(scaleCode)
                .orElseThrow(() -> new MasterDataNotFoundException("Grade Scale", scaleCode));
    }
}
