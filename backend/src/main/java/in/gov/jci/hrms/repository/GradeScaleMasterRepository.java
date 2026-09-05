package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.GradeScaleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeScaleMasterRepository extends JpaRepository<GradeScaleMaster, Long> {

    List<GradeScaleMaster> findAllByOrderByHierarchyLevelAsc();

    Optional<GradeScaleMaster> findByScaleCode(String scaleCode);
}
