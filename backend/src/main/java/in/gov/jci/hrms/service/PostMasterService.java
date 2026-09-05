package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PostIncumbencyResponse;
import in.gov.jci.hrms.dto.PostInventorySummaryResponse;
import in.gov.jci.hrms.dto.PostMasterRequest;
import in.gov.jci.hrms.dto.PostMasterResponse;
import in.gov.jci.hrms.dto.PostStatusUpdateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataInUseException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class PostMasterService {

    private static final String ENTITY_NAME = "Post";

    private final PostMasterRepository postMasterRepository;
    private final PostIncumbencyRepository postIncumbencyRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final RegionalOfficeRepository regionalOfficeRepository;
    private final DepartmentalPurchaseCentreRepository dpcRepository;

    public PostMasterService(PostMasterRepository postMasterRepository,
                              PostIncumbencyRepository postIncumbencyRepository,
                              DepartmentRepository departmentRepository,
                              DesignationRepository designationRepository,
                              RegionalOfficeRepository regionalOfficeRepository,
                              DepartmentalPurchaseCentreRepository dpcRepository) {
        this.postMasterRepository = postMasterRepository;
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.regionalOfficeRepository = regionalOfficeRepository;
        this.dpcRepository = dpcRepository;
    }

    @Transactional
    public PostMasterResponse create(PostMasterRequest request) {
        PostMaster post = new PostMaster(
                request.postCode(), request.title(),
                resolveDepartment(request.departmentId()), resolveDesignation(request.designationId()),
                request.active());
        post.setBudgeted(request.isBudgeted());
        applyOptionalFields(post, request, null);
        return PostMasterResponse.from(save(post));
    }

    public PostMasterResponse getById(Long id) {
        return PostMasterResponse.from(findOrThrow(id));
    }

    public Page<PostMasterResponse> list(Pageable pageable) {
        return postMasterRepository.findAll(pageable).map(PostMasterResponse::from);
    }

    /** GET /api/v1/posts - each filter is applied only when non-null; locationType is "HEAD_OFFICE"/"REGIONAL_OFFICE"/"DPC". */
    public Page<PostMasterResponse> search(Long departmentId, Long designationId, Long roId, Long dpcId,
                                            VacancyStatus vacancyStatus, Boolean isBudgeted, String locationType,
                                            String searchText, Pageable pageable) {
        String likePattern = searchText != null && !searchText.isBlank() ? "%" + searchText.toLowerCase() + "%" : null;
        return postMasterRepository.search(departmentId, designationId, roId, dpcId, vacancyStatus, isBudgeted,
                        locationType, likePattern, pageable)
                .map(PostMasterResponse::from);
    }

    /** GET /api/v1/posts/vacant - budgeted, vacant posts eligible for a new REGULAR assignment. */
    public Page<PostMasterResponse> listVacant(Pageable pageable) {
        return postMasterRepository.findByVacancyStatusAndBudgetedTrue(VacancyStatus.VACANT, pageable)
                .map(PostMasterResponse::from);
    }

    /**
     * GET /api/v1/posts/available-for-assignment - Edit Employee's "Vacant
     * Sanctioned Post" selector: VACANT+budgeted posts, plus whichever
     * post(s) employeeId currently holds (so the picker can display/
     * re-select the employee's own current post without it looking vacant).
     */
    public Page<PostMasterResponse> listAvailableForAssignment(Long employeeId, Long designationId, Long departmentId, Pageable pageable) {
        List<Long> currentPostIds = employeeId != null
                ? postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employeeId).stream()
                        .map(pi -> pi.getPost().getId())
                        .toList()
                : List.of();
        List<Long> safeCurrentPostIds = currentPostIds.isEmpty() ? List.of(-1L) : currentPostIds;
        return postMasterRepository.findAvailableForAssignment(departmentId, designationId, safeCurrentPostIds, pageable)
                .map(PostMasterResponse::from);
    }

    /** GET /api/v1/posts/summary - PIMS_SPEC.md Section 2.A's KPI metric bar. */
    public PostInventorySummaryResponse summary() {
        return new PostInventorySummaryResponse(
                postMasterRepository.count(),
                postMasterRepository.countByVacancyStatus(VacancyStatus.OCCUPIED),
                postMasterRepository.countByVacancyStatus(VacancyStatus.VACANT),
                postMasterRepository.countByVacancyStatus(VacancyStatus.FROZEN),
                postMasterRepository.countByVacancyStatus(VacancyStatus.ABOLISHED));
    }

    /** GET /api/v1/posts/:id/incumbency-history - PIMS_SPEC.md Section 2.C. */
    public List<PostIncumbencyResponse> incumbencyHistory(Long id) {
        findOrThrow(id);
        return postIncumbencyRepository.findByPostIdOrderByStartDateDesc(id).stream()
                .map(PostIncumbencyResponse::from)
                .toList();
    }

    @Transactional
    public PostMasterResponse update(Long id, PostMasterRequest request) {
        PostMaster post = findOrThrow(id);
        post.setPostCode(request.postCode());
        post.setTitle(request.title());
        post.setDepartment(resolveDepartment(request.departmentId()));
        post.setDesignation(resolveDesignation(request.designationId()));
        post.setActive(request.active());
        post.setBudgeted(request.isBudgeted());
        applyOptionalFields(post, request, id);
        return PostMasterResponse.from(save(post));
    }

    /**
     * PATCH /api/v1/posts/:id/status - PIMS_SPEC.md Section 2.C's "Change
     * Status" (Freeze / Unfreeze / Abolish) action, with an audit remark.
     * FROZEN: blocks new incumbency (fn_sync_post_vacancy_and_budget, V28)
     * without disturbing whoever currently holds it.
     * ABOLISHED: the seat itself is withdrawn - blocked if anyone is
     * currently the active incumbent.
     * VACANT/OCCUPIED ("Unfreeze"): only valid from FROZEN; which of the two
     * it resolves to is derived from whether an active incumbent already
     * exists, never trusted from the request.
     */
    @Transactional
    public PostMasterResponse updateStatus(Long id, PostStatusUpdateRequest request) {
        PostMaster post = findOrThrow(id);
        VacancyStatus target = request.targetStatus();

        if (target == VacancyStatus.ABOLISHED) {
            if (postIncumbencyRepository.existsByPostIdAndActiveTrue(id)) {
                throw new MasterDataInUseException(ENTITY_NAME, id);
            }
            post.setVacancyStatus(VacancyStatus.ABOLISHED);
        } else if (target == VacancyStatus.FROZEN) {
            post.setVacancyStatus(VacancyStatus.FROZEN);
        } else if (target == VacancyStatus.VACANT || target == VacancyStatus.OCCUPIED) {
            if (post.getVacancyStatus() != VacancyStatus.FROZEN) {
                throw new MasterDataValidationException(
                        "Post " + post.getPostCode() + " can only be unfrozen from FROZEN (currently " + post.getVacancyStatus() + ")");
            }
            boolean hasActiveIncumbent = postIncumbencyRepository.existsByPostIdAndActiveTrue(id);
            post.setVacancyStatus(hasActiveIncumbent ? VacancyStatus.OCCUPIED : VacancyStatus.VACANT);
        }

        // Left set on the entity (never persisted - @Transient) rather than
        // cleared here: the audit listener's @PreUpdate only fires when this
        // transaction actually flushes, which happens at commit, after this
        // method has already returned.
        post.setStatusChangeRemark(request.remark());
        return PostMasterResponse.from(post);
    }

    @Transactional
    public void delete(Long id) {
        PostMaster post = findOrThrow(id);
        if (postIncumbencyRepository.existsByPostIdAndActiveTrue(id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        if (postMasterRepository.existsByOperationalReportingPostIdOrAdministrativeReportingPostId(id, id)) {
            throw new MasterDataInUseException(ENTITY_NAME, id);
        }
        post.setActive(false);
        post.setDeletedAt(Instant.now());
    }

    private void applyOptionalFields(PostMaster post, PostMasterRequest request, Long selfId) {
        post.setRegionalOffice(resolveRegionalOffice(request.roId()));
        post.setDepartmentalPurchaseCentre(resolveDpc(request.dpcId()));
        post.setOperationalReportingPost(
                resolveReportingPost(request.operationalReportingPostId(), selfId, PostMaster::getOperationalReportingPost));
        post.setAdministrativeReportingPost(
                resolveReportingPost(request.administrativeReportingPostId(), selfId, PostMaster::getAdministrativeReportingPost));
        post.setAcceptingAuthorityPost(
                resolveReportingPost(request.acceptingAuthorityPostId(), selfId, PostMaster::getAcceptingAuthorityPost));
    }

    /**
     * Resolves a reporting-hierarchy target and rejects both direct
     * self-reference and any longer cycle (A -> B -> ... -> A) within that
     * same hierarchy type - PIMS_SPEC.md Section 2.B's "Cycle Prevention
     * Guard". Each of the three hierarchy fields (operational/
     * administrative/accepting-authority) is its own chain, walked via
     * nextInChain; a post existing in more than one chain isn't itself a
     * cycle.
     */
    private PostMaster resolveReportingPost(Long id, Long selfId, Function<PostMaster, PostMaster> nextInChain) {
        if (id == null) {
            return null;
        }
        PostMaster target = postMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));

        if (selfId != null) {
            Set<Long> visited = new HashSet<>();
            PostMaster cursor = target;
            while (cursor != null) {
                if (cursor.getId().equals(selfId)) {
                    throw new MasterDataValidationException(
                            "Reporting hierarchy would create a circular reference back to this post (via " + cursor.getPostCode() + ")");
                }
                if (!visited.add(cursor.getId())) {
                    break; // pre-existing cycle unrelated to selfId - not this call's problem to fix
                }
                cursor = nextInChain.apply(cursor);
            }
        }
        return target;
    }

    private Department resolveDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Department", id));
    }

    private Designation resolveDesignation(Long id) {
        return designationRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Designation", id));
    }

    private RegionalOffice resolveRegionalOffice(Long id) {
        if (id == null) {
            return null;
        }
        return regionalOfficeRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Regional Office", id));
    }

    private DepartmentalPurchaseCentre resolveDpc(Long id) {
        if (id == null) {
            return null;
        }
        return dpcRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("DPC", id));
    }

    private PostMaster findOrThrow(Long id) {
        return postMasterRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private PostMaster save(PostMaster post) {
        try {
            return postMasterRepository.saveAndFlush(post);
        } catch (DataIntegrityViolationException ex) {
            throw new MasterDataConflictException(ENTITY_NAME + " code already in use: " + post.getPostCode());
        }
    }
}
