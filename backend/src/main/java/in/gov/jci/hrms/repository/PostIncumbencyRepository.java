package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PostIncumbency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PostIncumbencyRepository extends JpaRepository<PostIncumbency, Long> {

    List<PostIncumbency> findByPostId(Long postId);

    /** GET /api/v1/posts/:id/incumbency-history - full chronological ledger, most recent first. */
    List<PostIncumbency> findByPostIdOrderByStartDateDesc(Long postId);

    List<PostIncumbency> findByEmployeeId(Long employeeId);

    List<PostIncumbency> findByPostIdAndActiveTrue(Long postId);

    List<PostIncumbency> findByEmployeeIdAndActiveTrue(Long employeeId);

    boolean existsByPostIdAndActiveTrue(Long postId);

    /**
     * An employee's single active incumbency, if any. Note this assumes at most one - there is no DB
     * constraint enforcing that per-EMPLOYEE (only uq_post_incumbency_post_substantive_active, which is
     * per-POST), so an employee genuinely holding e.g. an ADDITIONAL_CHARGE elsewhere on top of their
     * SUBSTANTIVE post would violate that assumption and this would throw NonUniqueResultException. Only
     * used where that's known not to apply (a plain movement/release/joining flow's own SUBSTANTIVE seat).
     */
    @Query("SELECT pi FROM PostIncumbency pi WHERE pi.employee.id = :employeeId AND pi.active = true AND pi.deletedAt IS NULL")
    Optional<PostIncumbency> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Closes ALL of an employee's active incumbencies (not scoped to SUBSTANTIVE) via a direct bulk
     * UPDATE - see RegularPayFixationRepository.closeCurrentFixation()'s javadoc for why a bulk query,
     * not entity mutation + save, is used here: PostIncumbencyService.create()'s own prior-incumbent
     * close (entity mutation, then saveAndFlush() of the new row) is structurally the same pattern that
     * caused RegularPayFixation's flush-ordering bug, and this avoids repeating it for the employee-side
     * close called from the movement lifecycle's release/joining hooks.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostIncumbency pi SET pi.active = false, pi.endDate = :endDate, pi.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE pi.employee.id = :employeeId AND pi.active = true AND pi.deletedAt IS NULL")
    int closeActiveIncumbency(@Param("employeeId") Long employeeId, @Param("endDate") LocalDate endDate);

    /**
     * Closes one specific incumbency row by its own id, via the same bulk-UPDATE pattern as
     * {@link #closeActiveIncumbency} - used by PostIncumbencyService.create() to close a post's
     * prior SUBSTANTIVE incumbent. That call site used to mutate the loaded {@code prior} entity
     * and rely on the enclosing @Transactional's end-of-method flush to persist it alongside the
     * saveAndFlush() of the brand-new incumbency row - structurally the exact same
     * entity-mutation-plus-later-flush pattern that caused RegularPayFixation's flush-ordering bug
     * (see RegularPayFixationRepository.closeCurrentFixation()): Hibernate orders INSERTs before
     * UPDATEs within one flush, so the new row's INSERT could hit
     * uq_post_incumbency_post_substantive_active before the old row's UPDATE ever ran. Keying by
     * id here (rather than post+employee) is deliberate: the caller already holds the exact row it
     * resolved and verified an outgoing movement for, so there is no risk of this closing the wrong
     * incumbency.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PostIncumbency pi SET pi.active = false, pi.endDate = :endDate, pi.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE pi.id = :id")
    int closeById(@Param("id") Long id, @Param("endDate") LocalDate endDate);
}
