package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacyServiceBook;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StagingLegacyServiceBookRepository extends JpaRepository<StagingLegacyServiceBook, Long> {

    List<StagingLegacyServiceBook> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);
}
