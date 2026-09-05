package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFunctionalRoleAssignment;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.repository.EmployeeFunctionalRoleAssignmentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves who approves an employee's leave/tour requests (FR-DOA.5/6) by
 * walking the post-reporting hierarchy: find the employee's current post,
 * follow operational_reporting_post_id up until a post with an active
 * Substantive incumbent is found (skipping vacant posts along the way).
 *
 * The static-employee-reporting-link fallback named in FR-DOA.6 is not
 * implemented: Employee has no manager/reporting-line field, and adding
 * one wasn't part of the V6 migration this service was built against.
 * staticFallback() is a deliberate stub (always empty) rather than a
 * guess at that data model - wire it up once that field exists.
 *
 * PIMS/ALMS Functional & Statutory Role Management Subsystem: this is the
 * actual approver-resolution service behind both ATTENDANCE_REGULARIZATION
 * (AttendanceRegularizationService) and leave applications including
 * CASUAL_LEAVE (LeaveApplicationService) - DoaResolverService is a
 * different, unrelated resolver (PIMS ALMS Phase 2 Section 6's DOA circular
 * routing), not used by either of those two workflows. So the "route to the
 * designated HOD before falling back to the hierarchy walk" requirement
 * lives here: resolveSupervisor() first checks for an active HOD functional
 * role assignment on the applicant's department and, if one exists and
 * isn't the applicant themselves, routes straight to that officer - only
 * falling through to the ordinary Substantive -> Additional Charge post
 * hierarchy walk when no such assignment is configured.
 */
@Service
@Transactional(readOnly = true)
public class SupervisorResolutionService {

    private static final int MAX_HIERARCHY_DEPTH = 20;
    private static final String HOD_ROLE_CODE = "HOD";

    private final PostIncumbencyRepository postIncumbencyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeFunctionalRoleAssignmentRepository functionalRoleAssignmentRepository;

    public SupervisorResolutionService(PostIncumbencyRepository postIncumbencyRepository,
                                        EmployeeRepository employeeRepository,
                                        EmployeeFunctionalRoleAssignmentRepository functionalRoleAssignmentRepository) {
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.employeeRepository = employeeRepository;
        this.functionalRoleAssignmentRepository = functionalRoleAssignmentRepository;
    }

    public record SupervisorResolution(Employee employee, PostMaster post) {
    }

    public Optional<SupervisorResolution> resolveSupervisor(Long employeeId) {
        Optional<SupervisorResolution> functionalHod = resolveFunctionalHod(employeeId);
        if (functionalHod.isPresent()) {
            return functionalHod;
        }

        Optional<PostMaster> currentPost = currentPost(employeeId);
        if (currentPost.isEmpty()) {
            return staticFallback(employeeId);
        }

        PostMaster post = currentPost.get();
        Set<Long> visited = new HashSet<>();
        visited.add(post.getId());

        for (int depth = 0; depth < MAX_HIERARCHY_DEPTH; depth++) {
            PostMaster reportingPost = post.getOperationalReportingPost();
            if (reportingPost == null || !visited.add(reportingPost.getId())) {
                // Top of the hierarchy, or a cycle in the reporting data - stop either way.
                break;
            }

            Optional<PostIncumbency> incumbency = activeSubstantiveIncumbency(reportingPost.getId());
            if (incumbency.isPresent()) {
                return Optional.of(new SupervisorResolution(incumbency.get().getEmployee(), reportingPost));
            }
            post = reportingPost;
        }

        return staticFallback(employeeId);
    }

    /**
     * The applicant's own department's active HOD assignment, if any -
     * excluding the applicant themselves (an HOD's own regularization/leave
     * request must not "route to" themselves; it falls through to the
     * ordinary hierarchy walk instead). Returned with a null post: no
     * PostMaster seat backs a functional-role assignment, and every
     * existing caller of resolveSupervisor() already handles Optional
     * results whose SupervisorResolution.post() may be null downstream
     * (LeaveApplication.approverPost / AttendanceRegularizationApplication
     * have no NOT NULL constraint on their approver-post columns).
     */
    private Optional<SupervisorResolution> resolveFunctionalHod(Long employeeId) {
        Employee applicant = employeeRepository.findById(employeeId).orElse(null);
        if (applicant == null || applicant.getDepartment() == null) {
            return Optional.empty();
        }

        List<EmployeeFunctionalRoleAssignment> activeHods = functionalRoleAssignmentRepository
                .findActiveByRoleCodeAndDepartment(HOD_ROLE_CODE, applicant.getDepartment().getId(), LocalDate.now());
        return activeHods.stream()
                .filter(assignment -> !assignment.getEmployee().getId().equals(employeeId))
                .findFirst()
                .map(assignment -> new SupervisorResolution(assignment.getEmployee(), null));
    }

    private Optional<PostMaster> currentPost(Long employeeId) {
        List<PostIncumbency> active = postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employeeId);
        return active.stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst()
                .or(() -> active.stream().findFirst())
                .map(PostIncumbency::getPost);
    }

    private Optional<PostIncumbency> activeSubstantiveIncumbency(Long postId) {
        return postIncumbencyRepository.findByPostIdAndActiveTrue(postId).stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst();
    }

    private Optional<SupervisorResolution> staticFallback(Long employeeId) {
        return Optional.empty();
    }
}
