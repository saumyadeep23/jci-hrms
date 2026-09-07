package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.VacancyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostMasterRepository extends JpaRepository<PostMaster, Long> {

    Optional<PostMaster> findByPostCode(String postCode);

    boolean existsByOperationalReportingPostIdOrAdministrativeReportingPostId(Long operationalReportingPostId,
                                                                               Long administrativeReportingPostId);

    long countByVacancyStatus(VacancyStatus vacancyStatus);

    /**
     * GET /api/v1/posts - each filter is applied only when non-null.
     * locationType is one of "HEAD_OFFICE" / "REGIONAL_OFFICE" / "DPC"
     * (PIMS_SPEC.md Section 2.A's "Location (HO vs. RO vs. DPC)" filter);
     * searchText matches post_code or title, case-insensitively.
     */
    @Query("SELECT p FROM PostMaster p WHERE "
            + "(:departmentId IS NULL OR p.department.id = :departmentId) AND "
            + "(:designationId IS NULL OR p.designation.id = :designationId) AND "
            + "(:roId IS NULL OR p.regionalOffice.id = :roId) AND "
            + "(:dpcId IS NULL OR p.departmentalPurchaseCentre.id = :dpcId) AND "
            + "(:vacancyStatus IS NULL OR p.vacancyStatus = :vacancyStatus) AND "
            + "(:isBudgeted IS NULL OR p.budgeted = :isBudgeted) AND "
            + "(:locationType IS NULL "
            + "  OR (:locationType = 'DPC' AND p.departmentalPurchaseCentre IS NOT NULL) "
            + "  OR (:locationType = 'HEAD_OFFICE' AND p.departmentalPurchaseCentre IS NULL AND p.regionalOffice.officeType = in.gov.jci.hrms.entity.OfficeType.HEAD_OFFICE) "
            + "  OR (:locationType = 'REGIONAL_OFFICE' AND p.departmentalPurchaseCentre IS NULL AND p.regionalOffice IS NOT NULL AND p.regionalOffice.officeType <> in.gov.jci.hrms.entity.OfficeType.HEAD_OFFICE)"
            + ") AND "
            + "(:searchText IS NULL OR LOWER(p.postCode) LIKE :searchText OR LOWER(p.title) LIKE :searchText)")
    Page<PostMaster> search(@Param("departmentId") Long departmentId, @Param("designationId") Long designationId,
                             @Param("roId") Long roId, @Param("dpcId") Long dpcId,
                             @Param("vacancyStatus") VacancyStatus vacancyStatus,
                             @Param("isBudgeted") Boolean isBudgeted,
                             @Param("locationType") String locationType,
                             @Param("searchText") String searchText,
                             Pageable pageable);

    /** GET /api/v1/posts/vacant - budgeted, vacant posts eligible for a new REGULAR assignment. */
    Page<PostMaster> findByVacancyStatusAndBudgetedTrue(VacancyStatus vacancyStatus, Pageable pageable);

    /**
     * GET /api/v1/posts/available-for-assignment - the Edit Employee "Vacant
     * Sanctioned Post" picker: budgeted VACANT posts, plus (so the picker can
     * still show/re-select) whichever post(s) the employee already holds
     * (currentPostIds), optionally narrowed by department/designation.
     * currentPostIds must never be passed empty - callers substitute a
     * sentinel (e.g. List.of(-1L)) since an empty JPQL IN-list is unsafe to
     * rely on across Hibernate versions.
     */
    @Query("SELECT p FROM PostMaster p WHERE "
            + "((p.vacancyStatus = in.gov.jci.hrms.entity.VacancyStatus.VACANT AND p.budgeted = true) OR p.id IN :currentPostIds) AND "
            + "(:departmentId IS NULL OR p.department.id = :departmentId) AND "
            + "(:designationId IS NULL OR p.designation.id = :designationId)")
    Page<PostMaster> findAvailableForAssignment(@Param("departmentId") Long departmentId,
                                                 @Param("designationId") Long designationId,
                                                 @Param("currentPostIds") java.util.List<Long> currentPostIds,
                                                 Pageable pageable);

    /** DoaResolverService's override-routing keyword lookup (e.g. "Managing Director", "Chief Vigilance Officer") - see its javadoc for why this is a best-effort title match, not a structured role lookup. */
    java.util.List<PostMaster> findByTitleContainingIgnoreCase(String titleFragment);

    /**
     * JoiningReportService's movement-to-post resolution: EmployeeMovementRecord itself carries no
     * postId (Movement Orders and the Post Incumbency ledger are otherwise two independent subsystems -
     * the Edit Employee "Vacant Sanctioned Post" picker is the only other place a post gets linked to an
     * employee today), so a joining's destination (department/designation/regionalOffice) is matched
     * against this table instead. Returns whatever matches, including zero or more than one - the
     * caller only acts when exactly one sanctioned post exists for that combination.
     */
    java.util.List<PostMaster> findByDepartment_IdAndDesignation_IdAndRegionalOffice_Id(Long departmentId, Long designationId, Long regionalOfficeId);
}
