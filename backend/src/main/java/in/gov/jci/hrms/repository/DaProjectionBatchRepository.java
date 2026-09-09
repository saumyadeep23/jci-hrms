package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DaProjectionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DaProjectionBatchRepository extends JpaRepository<DaProjectionBatch, Long> {

    boolean existsByProjectionCode(String projectionCode);
}
