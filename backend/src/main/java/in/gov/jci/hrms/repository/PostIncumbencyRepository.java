package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PostIncumbency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostIncumbencyRepository extends JpaRepository<PostIncumbency, Long> {

    List<PostIncumbency> findByPostId(Long postId);

    /** GET /api/v1/posts/:id/incumbency-history - full chronological ledger, most recent first. */
    List<PostIncumbency> findByPostIdOrderByStartDateDesc(Long postId);

    List<PostIncumbency> findByEmployeeId(Long employeeId);

    List<PostIncumbency> findByPostIdAndActiveTrue(Long postId);

    List<PostIncumbency> findByEmployeeIdAndActiveTrue(Long employeeId);

    boolean existsByPostIdAndActiveTrue(Long postId);
}
