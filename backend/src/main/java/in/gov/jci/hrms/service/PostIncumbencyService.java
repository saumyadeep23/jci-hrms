package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.MovementStatus;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * create() implements the "auto-close prior Substantive incumbent on new
 * appointment" requirement as service logic (rather than a DB trigger):
 * assigning a new SUBSTANTIVE incumbent to a post closes out whichever
 * SUBSTANTIVE incumbency was previously active on that same post. The V6
 * migration's partial unique index is the DB-level backstop if this is
 * ever bypassed.
 *
 * <p>That auto-close is only allowed to proceed if the outgoing incumbent has an actual
 * recorded exit: an {@code EmployeeMovementRecord} moving them away from this exact post
 * (department/designation/office) whose movementStatus has reached RELIEVED or JOINED. Without
 * that check, a wrong post picked on an unrelated joining would silently evict a still-serving
 * employee's incumbency with no trace of them ever having left - see
 * {@link EmployeeMovementRecordRepository#existsReleasedMovementFromPost}. When no such exit is
 * found, create() refuses the request rather than guessing; the post is left occupied and the
 * mismatch becomes a manual HR reconciliation task instead of a silent data change.
 */
@Service
@Transactional(readOnly = true)
public class PostIncumbencyService {

    private static final String ENTITY_NAME = "Post Incumbency";
    private static final List<MovementStatus> RELEASED_STATUSES = List.of(MovementStatus.RELIEVED, MovementStatus.JOINED);

    private final PostIncumbencyRepository postIncumbencyRepository;
    private final PostMasterRepository postMasterRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeMovementRecordRepository employeeMovementRecordRepository;

    public PostIncumbencyService(PostIncumbencyRepository postIncumbencyRepository,
                                  PostMasterRepository postMasterRepository,
                                  EmployeeRepository employeeRepository,
                                  EmployeeMovementRecordRepository employeeMovementRecordRepository) {
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.postMasterRepository = postMasterRepository;
        this.employeeRepository = employeeRepository;
        this.employeeMovementRecordRepository = employeeMovementRecordRepository;
    }

    @Transactional
    public PostIncumbencyResponse create(PostIncumbencyRequest request) {
        PostMaster post = resolvePost(request.postId());
        Employee employee = resolveEmployee(request.employeeId());

        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new MasterDataValidationException("endDate must not be before startDate");
        }

        if (request.assignmentType() == AssignmentType.SUBSTANTIVE) {
            closeActiveSubstantiveIncumbent(post, request.startDate());
        }

        PostIncumbency incumbency = new PostIncumbency(post, employee, request.assignmentType(), request.startDate());
        incumbency.setEndDate(request.endDate());
        incumbency.setOrderReference(request.orderReference());
        incumbency.setActive(true);

        return PostIncumbencyResponse.from(postIncumbencyRepository.saveAndFlush(incumbency));
    }

    public PostIncumbencyResponse getById(Long id) {
        return PostIncumbencyResponse.from(findOrThrow(id));
    }

    public Page<PostIncumbencyResponse> list(Pageable pageable) {
        return postIncumbencyRepository.findAll(pageable).map(PostIncumbencyResponse::from);
    }

    @Transactional
    public PostIncumbencyResponse end(Long id, LocalDate endDate) {
        PostIncumbency incumbency = findOrThrow(id);
        if (!incumbency.isActive()) {
            throw new MasterDataValidationException(ENTITY_NAME + " " + id + " is already ended");
        }
        if (endDate.isBefore(incumbency.getStartDate())) {
            throw new MasterDataValidationException("endDate must not be before startDate");
        }
        incumbency.setEndDate(endDate);
        incumbency.setActive(false);
        return PostIncumbencyResponse.from(incumbency);
    }

    private void closeActiveSubstantiveIncumbent(PostMaster post, LocalDate newStartDate) {
        postIncumbencyRepository.findByPostIdAndActiveTrue(post.getId()).stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .forEach(prior -> closeOutgoingIncumbent(post, prior, newStartDate));
    }

    private void closeOutgoingIncumbent(PostMaster post, PostIncumbency prior, LocalDate newStartDate) {
        Long officeId = post.getRegionalOffice() == null ? null : post.getRegionalOffice().getId();
        boolean hasRecordedExit = employeeMovementRecordRepository.existsReleasedMovementFromPost(
                prior.getEmployee().getId(), post.getDepartment().getId(), post.getDesignation().getId(),
                officeId, RELEASED_STATUSES);

        if (!hasRecordedExit) {
            throw new BusinessRuleViolationException("Post " + post.getPostCode() + " is still occupied by employee "
                    + prior.getEmployee().getId() + " (incumbency " + prior.getId()
                    + ") - no RELIEVED/JOINED movement out of this post was found for that employee. "
                    + "Outgoing incumbent not yet relieved: resolve this as a manual HR reconciliation "
                    + "before assigning a new substantive incumbent to this post.");
        }

        LocalDate closingDate = newStartDate.minusDays(1);
        if (closingDate.isBefore(prior.getStartDate())) {
            closingDate = prior.getStartDate();
        }
        // Bulk update, not entity mutation - see PostIncumbencyRepository.closeById()'s javadoc for
        // why (avoids repeating RegularPayFixation's flush-ordering bug against this same
        // create-a-new-row-while-closing-the-old-one shape).
        postIncumbencyRepository.closeById(prior.getId(), closingDate);
    }

    private PostMaster resolvePost(Long id) {
        return postMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Post", id));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private PostIncumbency findOrThrow(Long id) {
        return postIncumbencyRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
