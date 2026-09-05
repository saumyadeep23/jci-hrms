package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * create() implements the "auto-close prior Substantive incumbent on new
 * appointment" requirement as service logic (rather than a DB trigger):
 * assigning a new SUBSTANTIVE incumbent to a post closes out whichever
 * SUBSTANTIVE incumbency was previously active on that same post. The V6
 * migration's partial unique index is the DB-level backstop if this is
 * ever bypassed.
 */
@Service
@Transactional(readOnly = true)
public class PostIncumbencyService {

    private static final String ENTITY_NAME = "Post Incumbency";

    private final PostIncumbencyRepository postIncumbencyRepository;
    private final PostMasterRepository postMasterRepository;
    private final EmployeeRepository employeeRepository;

    public PostIncumbencyService(PostIncumbencyRepository postIncumbencyRepository,
                                  PostMasterRepository postMasterRepository,
                                  EmployeeRepository employeeRepository) {
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.postMasterRepository = postMasterRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public PostIncumbencyResponse create(PostIncumbencyRequest request) {
        PostMaster post = resolvePost(request.postId());
        Employee employee = resolveEmployee(request.employeeId());

        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new MasterDataValidationException("endDate must not be before startDate");
        }

        if (request.assignmentType() == AssignmentType.SUBSTANTIVE) {
            closeActiveSubstantiveIncumbent(post.getId(), request.startDate());
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

    private void closeActiveSubstantiveIncumbent(Long postId, LocalDate newStartDate) {
        postIncumbencyRepository.findByPostIdAndActiveTrue(postId).stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .forEach(prior -> {
                    LocalDate closingDate = newStartDate.minusDays(1);
                    if (closingDate.isBefore(prior.getStartDate())) {
                        closingDate = prior.getStartDate();
                    }
                    prior.setEndDate(closingDate);
                    prior.setActive(false);
                });
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
