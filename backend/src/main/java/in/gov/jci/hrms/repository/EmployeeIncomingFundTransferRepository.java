package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeIncomingFundTransfer;
import in.gov.jci.hrms.entity.IncomingTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeIncomingFundTransferRepository extends JpaRepository<EmployeeIncomingFundTransfer, Long> {

    List<EmployeeIncomingFundTransfer> findByEmployeeId(Long employeeId);

    List<EmployeeIncomingFundTransfer> findByStatus(IncomingTransferStatus status);

    Page<EmployeeIncomingFundTransfer> findByStatus(IncomingTransferStatus status, Pageable pageable);

    /** Sequence source for IncomingFundTransferService's "TRF-IN/{finYear}/{seq}" voucher numbering. */
    long countByTransferReferenceNoStartingWith(String prefix);
}
