package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.MovementLpcRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovementLpcRecordRepository extends JpaRepository<MovementLpcRecord, Long> {

    Optional<MovementLpcRecord> findByMovementId(Long movementId);
}
