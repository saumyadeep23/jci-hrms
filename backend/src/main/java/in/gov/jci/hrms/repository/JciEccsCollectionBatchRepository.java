package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsCollectionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JciEccsCollectionBatchRepository extends JpaRepository<JciEccsCollectionBatch, Long> {

    Optional<JciEccsCollectionBatch> findByPayrollRunId(String payrollRunId);
}
