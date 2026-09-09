package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanSettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CpfLoanSettlementRepository extends JpaRepository<CpfLoanSettlementTransaction, Long> {

    List<CpfLoanSettlementTransaction> findByLoanIdOrderByCreatedAtDesc(Long loanId);

    /** Sequence source for CpfLoanSettlementService's "CPFL-RCPT/{finYear}/{seq}" voucher numbering. */
    long countByReceiptVoucherNoStartingWith(String prefix);
}
