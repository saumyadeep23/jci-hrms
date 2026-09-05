package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.TerminalSettlementBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TerminalSettlementBeneficiaryRepository extends JpaRepository<TerminalSettlementBeneficiary, Long> {

    List<TerminalSettlementBeneficiary> findBySettlementId(Long settlementId);

    void deleteBySettlementId(Long settlementId);
}
