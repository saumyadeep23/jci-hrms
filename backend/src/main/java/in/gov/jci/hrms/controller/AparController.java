package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AparAcceptRequest;
import in.gov.jci.hrms.dto.EmployeeAparRequest;
import in.gov.jci.hrms.dto.EmployeeAparResponse;
import in.gov.jci.hrms.dto.ReportingAssessmentRequest;
import in.gov.jci.hrms.dto.RepresentationRequest;
import in.gov.jci.hrms.dto.ReviewingAssessmentRequest;
import in.gov.jci.hrms.dto.SelfAppraisalRequest;
import in.gov.jci.hrms.service.AparService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/apar")
public class AparController {

    private final AparService aparService;

    public AparController(AparService aparService) {
        this.aparService = aparService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<EmployeeAparResponse> initiate(@Valid @RequestBody EmployeeAparRequest request) {
        EmployeeAparResponse created = aparService.initiate(request);
        return ResponseEntity.created(URI.create("/api/apar/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isParticipant(authentication, #id)")
    public EmployeeAparResponse getById(@PathVariable Long id) {
        return aparService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Page<EmployeeAparResponse> list(Pageable pageable) {
        return aparService.list(pageable);
    }

    @PostMapping("/{id}/self-appraisal")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isSelf(authentication, #id)")
    public EmployeeAparResponse submitSelfAppraisal(@PathVariable Long id, @Valid @RequestBody SelfAppraisalRequest request) {
        return aparService.submitSelfAppraisal(id, request);
    }

    @PostMapping("/{id}/reporting-assessment")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isReportingOfficer(authentication, #id)")
    public EmployeeAparResponse submitReportingAssessment(@PathVariable Long id,
                                                            @Valid @RequestBody ReportingAssessmentRequest request) {
        return aparService.submitReportingAssessment(id, request);
    }

    @PostMapping("/{id}/reviewing-assessment")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isReviewingOfficer(authentication, #id)")
    public EmployeeAparResponse submitReviewingAssessment(@PathVariable Long id,
                                                            @Valid @RequestBody ReviewingAssessmentRequest request) {
        return aparService.submitReviewingAssessment(id, request);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isAcceptingAuthority(authentication, #id)")
    public EmployeeAparResponse accept(@PathVariable Long id, @Valid @RequestBody AparAcceptRequest request) {
        return aparService.accept(id, request);
    }

    @PostMapping("/{id}/disclose")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeAparResponse disclose(@PathVariable Long id) {
        return aparService.disclose(id);
    }

    @PostMapping("/{id}/representation")
    @PreAuthorize("hasRole('HR_ADMIN') or @aparSec.isSelf(authentication, #id)")
    public EmployeeAparResponse submitRepresentation(@PathVariable Long id, @Valid @RequestBody RepresentationRequest request) {
        return aparService.submitRepresentation(id, request);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeAparResponse finalizeApar(@PathVariable Long id) {
        return aparService.finalizeApar(id);
    }
}
