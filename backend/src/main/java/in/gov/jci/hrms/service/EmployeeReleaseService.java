package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared "close out an employee's active service" logic - the single
 * source of truth both SuperannuationScheduledTask's daily sweep and
 * ExitClearanceService.finalizeReleaseOrder() rely on, so the two triggers
 * (an unconditional date-based safety net vs. a clearance-gated release
 * order) can never drift apart on what "released" actually means:
 * vacate the active SUBSTANTIVE post_incumbency, close the active
 * regular_pay_fixations row, and flip employee status.
 */
@Service
public class EmployeeReleaseService {

    private final PostIncumbencyService postIncumbencyService;
    private final PostIncumbencyRepository postIncumbencyRepository;
    private final PostMasterRepository postMasterRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final AuditLogRecorder auditLogRecorder;

    public EmployeeReleaseService(PostIncumbencyService postIncumbencyService,
                                   PostIncumbencyRepository postIncumbencyRepository,
                                   PostMasterRepository postMasterRepository,
                                   RegularPayFixationRepository regularPayFixationRepository,
                                   AuditLogRecorder auditLogRecorder) {
        this.postIncumbencyService = postIncumbencyService;
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.postMasterRepository = postMasterRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.auditLogRecorder = auditLogRecorder;
    }

    @Transactional
    public void release(Employee employee, LocalDate releaseDate, EmployeeStatus targetStatus, String auditReason) {
        EmployeeStatus previousStatus = employee.getStatus();
        employee.setStatus(targetStatus);

        postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employee.getId()).stream()
                .filter(pi -> pi.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst()
                .ifPresent(incumbency -> {
                    PostMaster post = incumbency.getPost();
                    postIncumbencyService.end(incumbency.getId(), releaseDate);
                    post.setVacancyStatus(VacancyStatus.VACANT);
                    postMasterRepository.save(post);
                });

        regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .ifPresent((RegularPayFixation fixation) -> {
                    fixation.setCurrent(false);
                    fixation.setEffectiveTo(releaseDate);
                });

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", previousStatus);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", targetStatus);
        after.put("releaseDate", releaseDate);
        after.put("reason", auditReason);
        auditLogRecorder.record("Employee", employee.getId(), AuditAction.UPDATE, before, after);
    }
}
