package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.TerminalSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TerminalSettlementRepository extends JpaRepository<TerminalSettlement, Long> {

    List<TerminalSettlement> findByEmployeeId(Long employeeId);

    /** Most recently generated settlement for an employee - generate() creates a new row each call rather than updating in place, so this is what "the" settlement means for preview/approve endpoints. */
    Optional<TerminalSettlement> findFirstByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
