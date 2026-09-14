package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfWithdrawalTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpfWithdrawalTypeMasterRepository extends JpaRepository<CpfWithdrawalTypeMaster, UUID> {

    List<CpfWithdrawalTypeMaster> findAllByOrderByDisplayOrderAsc();

    Optional<CpfWithdrawalTypeMaster> findByCode(String code);
}
