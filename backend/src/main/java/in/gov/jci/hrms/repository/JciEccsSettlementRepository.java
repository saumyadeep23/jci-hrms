package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JciEccsSettlementRepository extends JpaRepository<JciEccsSettlement, Long> {

    Optional<JciEccsSettlement> findByMember_Id(Long memberId);

    Optional<JciEccsSettlement> findByEmployeeId(Long employeeId);
}
