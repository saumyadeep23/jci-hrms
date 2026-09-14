package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfWithdrawalPurposeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpfWithdrawalPurposeMasterRepository extends JpaRepository<CpfWithdrawalPurposeMaster, UUID> {

    List<CpfWithdrawalPurposeMaster> findAllByOrderByCodeAsc();

    Optional<CpfWithdrawalPurposeMaster> findByCode(String code);
}
