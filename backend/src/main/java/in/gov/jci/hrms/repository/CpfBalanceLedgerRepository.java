package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfBalanceLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CpfBalanceLedgerRepository extends JpaRepository<CpfBalanceLedger, Long> {

    Optional<CpfBalanceLedger> findByEmployeeId(Long employeeId);
}
