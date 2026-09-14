package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfApplicationLedgerAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfApplicationLedgerAllocationRepository extends JpaRepository<CpfApplicationLedgerAllocation, UUID> {

    List<CpfApplicationLedgerAllocation> findByApplication_Id(UUID applicationId);
}
