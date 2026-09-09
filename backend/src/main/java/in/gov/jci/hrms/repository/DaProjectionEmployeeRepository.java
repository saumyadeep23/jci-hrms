package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DaProjectionEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DaProjectionEmployeeRepository extends JpaRepository<DaProjectionEmployee, Long> {

    List<DaProjectionEmployee> findByBatch_Id(Long batchId);

    /** checkCutoffAndRollOver()'s re-simulation starts from a clean slate, same rule as PayrollBatchComputationService.processBatch()'s own re-run. */
    @Modifying
    @Transactional
    long deleteByBatch_Id(Long batchId);
}
