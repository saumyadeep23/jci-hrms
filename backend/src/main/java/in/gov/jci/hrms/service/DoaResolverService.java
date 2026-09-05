package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PostIncumbency;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * DOA Dynamic Resolver (PIMS ALMS Phase 2, Section 6, Circular 2026-27/01) -
 * a distinct resolver from SupervisorResolutionService (FR-DOA.5/6), which
 * only ever accepts a SUBSTANTIVE incumbent when walking the reporting
 * hierarchy. This resolver additionally accepts an ADDITIONAL_CHARGE holder
 * at the same post before walking further up, per the circular's explicit
 * "Substantive -> Additional Charge -> static fallback" order, and layers
 * override routing on top.
 *
 * Override routing (>=E4/RM -> MD; HR/Finance -> Director (Finance);
 * Vigilance -> CVO; Field -> RM) is a best-effort approximation, not an
 * exact implementation: this schema has no numeric post-grade field
 * (Designation is a free-text title, e.g. "Manager (E4)") and no structured
 * MD/CVO/"Director (Finance)" role concept. Overrides are resolved by a
 * case-insensitive title-keyword search (PostMasterRepository.
 * findByTitleContainingIgnoreCase) against an ACTIVE post with an active
 * incumbent; if no such post/incumbent is found, resolution falls through to
 * the ordinary hierarchy walk rather than failing outright.
 */
@Service
@Transactional(readOnly = true)
public class DoaResolverService {

    private static final int MAX_HIERARCHY_DEPTH = 20;
    private static final List<String> GRADE_E4_PLUS_MARKERS = List.of("E4", "E5", "E6", "E7", "E8", "E9");
    private static final List<String> RM_MARKERS = List.of("Regional Manager", "RM");
    private static final String MD_TITLE_KEYWORD = "Managing Director";
    private static final String DIRECTOR_FINANCE_TITLE_KEYWORD = "Director (Finance)";
    private static final String CVO_TITLE_KEYWORD = "Chief Vigilance Officer";

    public record DoaResolution(Employee employee, PostMaster post, String routingReason) {
    }

    private final PostIncumbencyRepository postIncumbencyRepository;
    private final PostMasterRepository postMasterRepository;

    public DoaResolverService(PostIncumbencyRepository postIncumbencyRepository, PostMasterRepository postMasterRepository) {
        this.postIncumbencyRepository = postIncumbencyRepository;
        this.postMasterRepository = postMasterRepository;
    }

    public Optional<DoaResolution> resolveApprover(Long employeeId) {
        Optional<PostMaster> currentPost = currentPost(employeeId);
        if (currentPost.isEmpty()) {
            return Optional.empty();
        }

        Optional<DoaResolution> override = resolveOverride(currentPost.get());
        if (override.isPresent()) {
            return override;
        }
        return resolveViaHierarchy(currentPost.get());
    }

    private Optional<DoaResolution> resolveOverride(PostMaster fromPost) {
        String title = fromPost.getTitle();
        String departmentName = fromPost.getDepartment() != null ? fromPost.getDepartment().getName() : "";

        boolean gradeOrRmOverride = GRADE_E4_PLUS_MARKERS.stream().anyMatch(marker -> containsWord(title, marker))
                || RM_MARKERS.stream().anyMatch(marker -> title.toLowerCase().contains(marker.toLowerCase()));
        if (gradeOrRmOverride) {
            Optional<DoaResolution> md = resolveByTitleKeyword(MD_TITLE_KEYWORD, "Grade >= E4 / RM routes to MD (Circular 2026-27/01)");
            if (md.isPresent()) {
                return md;
            }
        }

        if (containsWord(departmentName, "HR") || departmentName.toLowerCase().contains("finance")) {
            Optional<DoaResolution> directorFinance = resolveByTitleKeyword(DIRECTOR_FINANCE_TITLE_KEYWORD,
                    "HR/Finance department routes to Director (Finance) (Circular 2026-27/01)");
            if (directorFinance.isPresent()) {
                return directorFinance;
            }
        }

        if (departmentName.toLowerCase().contains("vigilance")) {
            Optional<DoaResolution> cvo = resolveByTitleKeyword(CVO_TITLE_KEYWORD,
                    "Vigilance department routes to CVO (Circular 2026-27/01)");
            if (cvo.isPresent()) {
                return cvo;
            }
        }

        // "Field to RM" is left to the ordinary hierarchy walk: a field employee's
        // own reporting chain already terminates at their Regional Manager in the
        // normal RO/DPC post structure, so no separate override lookup is needed.
        return Optional.empty();
    }

    private Optional<DoaResolution> resolveByTitleKeyword(String titleKeyword, String reason) {
        return postMasterRepository.findByTitleContainingIgnoreCase(titleKeyword).stream()
                .filter(PostMaster::isActive)
                .findFirst()
                .flatMap(post -> activeIncumbent(post.getId())
                        .map(incumbency -> new DoaResolution(incumbency.getEmployee(), post, reason)));
    }

    private Optional<DoaResolution> resolveViaHierarchy(PostMaster startPost) {
        PostMaster post = startPost;
        Set<Long> visited = new HashSet<>();
        visited.add(post.getId());

        for (int depth = 0; depth < MAX_HIERARCHY_DEPTH; depth++) {
            PostMaster reportingPost = post.getOperationalReportingPost();
            if (reportingPost == null || !visited.add(reportingPost.getId())) {
                break;
            }

            Optional<PostIncumbency> incumbency = activeIncumbent(reportingPost.getId());
            if (incumbency.isPresent()) {
                String reason = incumbency.get().getAssignmentType() == AssignmentType.SUBSTANTIVE
                        ? "Substantive holder" : "Additional Charge holder (no Substantive holder at this post)";
                return Optional.of(new DoaResolution(incumbency.get().getEmployee(), reportingPost, reason));
            }
            post = reportingPost;
        }
        return Optional.empty();
    }

    /** Substantive holder preferred; falls back to an Additional Charge holder at the same post - LOOK_AFTER/ACTING are not accepted as an approver, matching SupervisorResolutionService's stricter stance. */
    private Optional<PostIncumbency> activeIncumbent(Long postId) {
        List<PostIncumbency> active = postIncumbencyRepository.findByPostIdAndActiveTrue(postId);
        return active.stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst()
                .or(() -> active.stream()
                        .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.ADDITIONAL_CHARGE)
                        .min(Comparator.comparing(PostIncumbency::getStartDate)));
    }

    private Optional<PostMaster> currentPost(Long employeeId) {
        List<PostIncumbency> active = postIncumbencyRepository.findByEmployeeIdAndActiveTrue(employeeId);
        return active.stream()
                .filter(incumbency -> incumbency.getAssignmentType() == AssignmentType.SUBSTANTIVE)
                .findFirst()
                .or(() -> active.stream().findFirst())
                .map(PostIncumbency::getPost);
    }

    private boolean containsWord(String haystack, String word) {
        if (haystack == null) {
            return false;
        }
        return haystack.toLowerCase().contains(word.toLowerCase());
    }
}
