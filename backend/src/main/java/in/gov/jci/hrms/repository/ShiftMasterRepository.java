package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ShiftMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShiftMasterRepository extends JpaRepository<ShiftMaster, Long> {

    Optional<ShiftMaster> findByShiftCode(String shiftCode);
}
