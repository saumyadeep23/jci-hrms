package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AparAcceptRequest;
import in.gov.jci.hrms.dto.EmployeeAparRequest;
import in.gov.jci.hrms.dto.EmployeeAparResponse;
import in.gov.jci.hrms.dto.ReportingAssessmentRequest;
import in.gov.jci.hrms.dto.RepresentationRequest;
import in.gov.jci.hrms.dto.ReviewingAssessmentRequest;
import in.gov.jci.hrms.dto.SelfAppraisalRequest;
import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeApar;
import in.gov.jci.hrms.entity.EmployeeAparStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeAparRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-employee 4-tier APAR lifecycle: Initiate (Draft) -> Self-Appraisal ->
 * Reporting Assessment -> Reviewing Officer -> Accepting Authority, then
 * FR-APAR.4 disclosure to the employee and FR-APAR.5's optional
 * representation/grievance before finalization:
 *
 * DRAFT -> SUBMITTED_BY_EMPLOYEE -> REPORTED -> REVIEWED -> ACCEPTED
 *       -> DISCLOSED -[optional]-> REPRESENTATION_SUBMITTED -> FINALIZED
 *
 * finalize() is reachable from either DISCLOSED (no grievance filed) or
 * REPRESENTATION_SUBMITTED (grievance filed and administratively closed
 * out) - most employees won't contest their APAR, so a representation is
 * an optional detour, not a mandatory step.
 */
@Service
@Transactional(readOnly = true)
public class AparService {

    private static final String ENTITY_NAME = "Employee APAR";

    private final EmployeeAparRepository employeeAparRepository;
    private final EmployeeRepository employeeRepository;
    private final AparCycleService aparCycleService;

    public AparService(EmployeeAparRepository employeeAparRepository, EmployeeRepository employeeRepository,
                        AparCycleService aparCycleService) {
        this.employeeAparRepository = employeeAparRepository;
        this.employeeRepository = employeeRepository;
        this.aparCycleService = aparCycleService;
    }

    @Transactional
    public EmployeeAparResponse initiate(EmployeeAparRequest request) {
        AparCycle cycle = aparCycleService.findOrThrow(request.aparCycleId());
        Employee employee = resolveEmployee(request.employeeId());
        Employee reportingOfficer = resolveEmployee(request.reportingOfficerId());
        Employee reviewingOfficer = resolveEmployee(request.reviewingOfficerId());
        Employee acceptingAuthority = resolveEmployee(request.acceptingAuthorityId());

        EmployeeApar apar = new EmployeeApar(cycle, employee, reportingOfficer, reviewingOfficer, acceptingAuthority);
        try {
            return EmployeeAparResponse.from(employeeAparRepository.saveAndFlush(apar));
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(
                    ENTITY_NAME + " already exists for employee " + request.employeeId() + " in this cycle");
        }
    }

    public EmployeeAparResponse getById(Long id) {
        return EmployeeAparResponse.from(findOrThrow(id));
    }

    public Page<EmployeeAparResponse> list(Pageable pageable) {
        return employeeAparRepository.findAll(pageable).map(EmployeeAparResponse::from);
    }

    @Transactional
    public EmployeeAparResponse submitSelfAppraisal(Long id, SelfAppraisalRequest request) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.DRAFT);

        apar.setSelfAppraisalText(request.selfAppraisalText());
        apar.setStatus(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE);
        return EmployeeAparResponse.from(apar);
    }

    @Transactional
    public EmployeeAparResponse submitReportingAssessment(Long id, ReportingAssessmentRequest request) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE);

        apar.setReportingScore(request.reportingScore());
        apar.setReportingRemarks(request.reportingRemarks());
        apar.setStatus(EmployeeAparStatus.REPORTED);
        return EmployeeAparResponse.from(apar);
    }

    @Transactional
    public EmployeeAparResponse submitReviewingAssessment(Long id, ReviewingAssessmentRequest request) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.REPORTED);

        apar.setReviewingScore(request.reviewingScore());
        apar.setReviewingRemarks(request.reviewingRemarks());
        apar.setStatus(EmployeeAparStatus.REVIEWED);
        return EmployeeAparResponse.from(apar);
    }

    @Transactional
    public EmployeeAparResponse accept(Long id, AparAcceptRequest request) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.REVIEWED);

        apar.setFinalScore(request.finalScore());
        apar.setFinalGrading(request.finalGrading());
        apar.setStatus(EmployeeAparStatus.ACCEPTED);
        return EmployeeAparResponse.from(apar);
    }

    /**
     * FR-APAR.4: makes the final grading visible to the employee.
     */
    @Transactional
    public EmployeeAparResponse disclose(Long id) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.ACCEPTED);

        apar.setStatus(EmployeeAparStatus.DISCLOSED);
        return EmployeeAparResponse.from(apar);
    }

    /**
     * FR-APAR.5: optional grievance against the disclosed grading.
     */
    @Transactional
    public EmployeeAparResponse submitRepresentation(Long id, RepresentationRequest request) {
        EmployeeApar apar = findOrThrow(id);
        requireStatus(apar, EmployeeAparStatus.DISCLOSED);

        apar.setRepresentationText(request.representationText());
        apar.setStatus(EmployeeAparStatus.REPRESENTATION_SUBMITTED);
        return EmployeeAparResponse.from(apar);
    }

    @Transactional
    public EmployeeAparResponse finalizeApar(Long id) {
        EmployeeApar apar = findOrThrow(id);
        if (apar.getStatus() != EmployeeAparStatus.DISCLOSED && apar.getStatus() != EmployeeAparStatus.REPRESENTATION_SUBMITTED) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " must be DISCLOSED or REPRESENTATION_SUBMITTED but is " + apar.getStatus());
        }
        apar.setStatus(EmployeeAparStatus.FINALIZED);
        return EmployeeAparResponse.from(apar);
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private void requireStatus(EmployeeApar apar, EmployeeAparStatus expected) {
        if (apar.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + apar.getId() + " must be " + expected + " but is " + apar.getStatus());
        }
    }

    private EmployeeApar findOrThrow(Long id) {
        return employeeAparRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
